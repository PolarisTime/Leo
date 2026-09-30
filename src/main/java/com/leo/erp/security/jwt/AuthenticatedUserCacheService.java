package com.leo.erp.security.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.api.AuthenticationAccountQuery;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.security.config.AuthFallbackProperties;
import com.leo.erp.security.support.SecurityPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AuthenticatedUserCacheService {

    private static final String USER_CACHE_PREFIX = "auth:user:snapshot:";
    private static final String USER_CACHE_INDEX_KEY = "auth:user:snapshot:index";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthenticationAccountQuery authenticationAccountQuery;
    private final RedisTuningProperties redisTuningProperties;
    private final AuthFallbackProperties authFallbackProperties;
    private final DefaultRedisScript<Long> snapshotWriteScript;

    /**
     * 进程内「有界降级快照」：只在 Redis 读取失败时使用，详见 {@link AuthFallbackProperties}。
     *
     * <p>每次成功读到主体都会刷新这里，因此窗口内的数据最多比 Redis 旧 ttl 秒；
     * 这是「Redis 抖动时全站 500」与「吊销最多延迟 ttl 秒生效」之间的取舍，
     * 由 {@code leo.security.auth-fallback.*} 控制，可设为 0 关闭。</p>
     */
    private final Map<Long, FallbackSnapshot> fallbackSnapshots = new ConcurrentHashMap<>();

    @Autowired
    public AuthenticatedUserCacheService(StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         AuthenticationAccountQuery authenticationAccountQuery,
                                         RedisTuningProperties redisTuningProperties,
                                         AuthFallbackProperties authFallbackProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authenticationAccountQuery = authenticationAccountQuery;
        this.redisTuningProperties = redisTuningProperties;
        this.authFallbackProperties = authFallbackProperties == null ? new AuthFallbackProperties() : authFallbackProperties;
        this.snapshotWriteScript = new DefaultRedisScript<>();
        this.snapshotWriteScript.setLocation(new ClassPathResource("db/authenticated_user_snapshot_write.lua"));
        this.snapshotWriteScript.setResultType(Long.class);
    }

    public Optional<SecurityPrincipal> getActivePrincipal(Long userId) {
        return getActivePrincipal(userId, null);
    }

    public Optional<SecurityPrincipal> getActivePrincipal(Long userId, long credentialVersion) {
        return getActivePrincipal(userId, Long.valueOf(credentialVersion));
    }

    private Optional<SecurityPrincipal> getActivePrincipal(Long userId, Long expectedCredentialVersion) {
        if (userId == null) {
            return Optional.empty();
        }

        String cacheKey = cacheKey(userId);
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException ex) {
            return degradeOnRedisFailure(userId, expectedCredentialVersion, ex);
        }
        if (cached != null && !cached.isBlank()) {
            Optional<SecurityPrincipal> principal = parseCachedPrincipal(cacheKey, cached);
            if (principal.isPresent() && credentialVersionMatches(principal.get(), expectedCredentialVersion)) {
                rememberFallbackSnapshot(principal.get());
                return principal;
            }
            if (principal.isPresent()) {
                try {
                    redisTemplate.delete(cacheKey);
                } catch (RuntimeException ex) {
                    log.warn("认证缓存失效清理失败（忽略，按未命中处理）: userId={} reason={}", userId, ex.getClass().getSimpleName());
                }
            }
        }

        return loadAndCachePrincipal(userId, cacheKey, expectedCredentialVersion);
    }

    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(cacheKey(userId));
        redisTemplate.opsForSet().remove(USER_CACHE_INDEX_KEY, String.valueOf(userId));
    }

    public void evictAll() {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(USER_CACHE_INDEX_KEY))) {
            evictAllByScanFallback();
            return;
        }
        List<String> keys = new ArrayList<>(redisTuningProperties.deleteBatchSize());
        try (var cursor = redisTemplate.opsForSet().scan(
                USER_CACHE_INDEX_KEY,
                ScanOptions.scanOptions().count(redisTuningProperties.scanBatchSize()).build())) {
            while (cursor.hasNext()) {
                parseUserId(cursor.next()).map(this::cacheKey).ifPresent(keys::add);
                if (keys.size() >= redisTuningProperties.deleteBatchSize()) {
                    redisTemplate.delete(keys);
                    keys.clear();
                }
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(USER_CACHE_INDEX_KEY);
    }

    private void evictAllByScanFallback() {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }
        log.warn("Authenticated user cache index unavailable, falling back to bounded SCAN eviction");
        int deleted = 0;
        List<String> keys = new ArrayList<>(redisTuningProperties.deleteBatchSize());
        RedisConnection connection = connectionFactory.getConnection();
        try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                .match(USER_CACHE_PREFIX + "*")
                .count(redisTuningProperties.scanBatchSize())
                .build())) {
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                if (keys.size() >= redisTuningProperties.deleteBatchSize()) {
                    redisTemplate.delete(keys);
                    deleted += keys.size();
                    keys.clear();
                    if (deleted >= redisTuningProperties.maxScanKeys()) {
                        log.warn("Authenticated user cache scan reached max limit, deleted={}", deleted);
                        break;
                    }
                }
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                deleted += keys.size();
            }
        } catch (RuntimeException ex) {
            log.warn("Authenticated user cache scan eviction failed", ex);
        } finally {
            try {
                connection.close();
            } catch (RuntimeException ex) {
                log.warn("Redis connection close failed after authenticated user cache scan eviction", ex);
            }
        }
    }

    private Optional<SecurityPrincipal> parseCachedPrincipal(String cacheKey, String cached) {
        try {
            CachedAuthenticatedUser snapshot = objectMapper.readValue(cached, CachedAuthenticatedUser.class);
            return Optional.of(snapshot.toPrincipal());
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    private Optional<SecurityPrincipal> loadAndCachePrincipal(
            Long userId,
            String cacheKey,
            Long expectedCredentialVersion
    ) {
        Optional<SecurityPrincipal> principal;
        try {
            principal = authenticationAccountQuery.findActiveById(userId)
                    .map(this::toSnapshot)
                    .filter(snapshot -> expectedCredentialVersion == null
                            || snapshot.credentialVersion() == expectedCredentialVersion)
                    .map(CachedAuthenticatedUser::toPrincipal);
        } catch (RuntimeException ex) {
            // 数据源不可用属于基础设施故障，同样交给降级/503 处理，避免伪装成 500
            return degradeOnRedisFailure(userId, expectedCredentialVersion, ex);
        }
        principal.ifPresent(value -> {
            rememberFallbackSnapshot(value);
            try {
                writeSnapshot(cacheKey, toSnapshot(value, userId));
            } catch (RuntimeException ex) {
                // 写缓存失败不影响本次认证结果：DB 是权威来源，Redis 只是加速层
                log.warn("认证缓存回填失败（忽略）: userId={} reason={}", userId, ex.getClass().getSimpleName());
            }
        });
        return principal;
    }

    /**
     * Redis 读取失败时的降级处理。
     *
     * <p>顺序：先在「有界降级窗口」内查找快照（命中即放行，保证 Redis 抖动时登录态不崩），
     * 窗口未命中则抛 {@link AuthenticationInfrastructureException}（HTTP 503），
     * 而不是让 {@code RedisConnectionFailureException} 穿透成 500。</p>
     */
    private Optional<SecurityPrincipal> degradeOnRedisFailure(Long userId, Long expectedCredentialVersion, RuntimeException cause) {
        Optional<SecurityPrincipal> snapshot = findFallbackSnapshot(userId, expectedCredentialVersion);
        if (snapshot.isPresent()) {
            log.warn("Redis 不可用，使用进程内降级快照放行: userId={} ttlSeconds={} reason={}",
                    userId, authFallbackProperties.getTtlSeconds(), cause.getClass().getSimpleName());
            return snapshot;
        }
        throw new AuthenticationInfrastructureException(
                "认证依赖（Redis）不可用，无法校验登录状态，请稍后重试", cause);
    }

    private void rememberFallbackSnapshot(SecurityPrincipal principal) {
        if (!authFallbackProperties.isEnabled() || principal == null || principal.id() == null) {
            return;
        }
        if (fallbackSnapshots.size() >= authFallbackProperties.getMaxEntries()
                && !fallbackSnapshots.containsKey(principal.id())) {
            evictExpiredSnapshots();
            if (fallbackSnapshots.size() >= authFallbackProperties.getMaxEntries()) {
                // 仍然超额时丢弃最早到期的一条，保证内存有界（宁可少一份降级能力，也不无界增长）
                fallbackSnapshots.entrySet().stream()
                        .min(Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()))
                        .map(Map.Entry::getKey)
                        .ifPresent(fallbackSnapshots::remove);
            }
        }
        fallbackSnapshots.put(principal.id(), new FallbackSnapshot(
                principal,
                System.currentTimeMillis() + authFallbackProperties.ttl().toMillis()));
    }

    private Optional<SecurityPrincipal> findFallbackSnapshot(Long userId, Long expectedCredentialVersion) {
        if (!authFallbackProperties.isEnabled()) {
            return Optional.empty();
        }
        FallbackSnapshot snapshot = fallbackSnapshots.get(userId);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (snapshot.isExpired()) {
            fallbackSnapshots.remove(userId, snapshot);
            return Optional.empty();
        }
        return credentialVersionMatches(snapshot.principal(), expectedCredentialVersion)
                ? Optional.of(snapshot.principal())
                : Optional.empty();
    }

    private void evictExpiredSnapshots() {
        fallbackSnapshots.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private CachedAuthenticatedUser toSnapshot(SecurityPrincipal principal, Long userId) {
        return new CachedAuthenticatedUser(userId, principal.username(), principal.credentialVersion());
    }

    /** 供测试观察降级窗口的当前用量。 */
    int fallbackSnapshotCount() {
        return fallbackSnapshots.size();
    }

    private record FallbackSnapshot(SecurityPrincipal principal, long expiresAtMillis) {

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private CachedAuthenticatedUser toSnapshot(
            AuthenticationAccountQuery.AuthenticatedAccountSnapshot account
    ) {
        return new CachedAuthenticatedUser(
                account.userId(),
                account.loginName(),
                account.credentialVersion()
        );
    }

    private boolean credentialVersionMatches(SecurityPrincipal principal, Long expectedCredentialVersion) {
        return expectedCredentialVersion == null || principal.credentialVersion() == expectedCredentialVersion;
    }

    private void writeSnapshot(String cacheKey, CachedAuthenticatedUser snapshot) {
        try {
            redisTemplate.execute(
                    snapshotWriteScript,
                    List.of(cacheKey, USER_CACHE_INDEX_KEY),
                    objectMapper.writeValueAsString(snapshot),
                    String.valueOf(redisTuningProperties.withTtlJitter(redisTuningProperties.authUserTtl()).toMillis()),
                    String.valueOf(redisTuningProperties.authUserIndexTtl().toMillis()),
                    String.valueOf(snapshot.userId())
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("认证用户缓存序列化失败", ex);
        }
    }

    private Optional<Long> parseUserId(String rawValue) {
        try {
            return Optional.of(Long.parseLong(rawValue));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String cacheKey(Long userId) {
        return USER_CACHE_PREFIX + userId;
    }

    private record CachedAuthenticatedUser(
            Long userId,
            String loginName,
            long credentialVersion
    ) {

        private SecurityPrincipal toPrincipal() {
            return SecurityPrincipal.authenticated(
                    userId,
                    loginName,
                    credentialVersion
            );
        }
    }
}
