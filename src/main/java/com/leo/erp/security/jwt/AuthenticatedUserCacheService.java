package com.leo.erp.security.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.api.AuthenticationAccountQuery;
import com.leo.erp.common.config.RedisTuningProperties;
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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuthenticatedUserCacheService {

    private static final String USER_CACHE_PREFIX = "auth:user:snapshot:";
    private static final String USER_CACHE_INDEX_KEY = "auth:user:snapshot:index";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthenticationAccountQuery authenticationAccountQuery;
    private final RedisTuningProperties redisTuningProperties;
    private final DefaultRedisScript<Long> snapshotWriteScript;

    @Autowired
    public AuthenticatedUserCacheService(StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         AuthenticationAccountQuery authenticationAccountQuery,
                                         RedisTuningProperties redisTuningProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authenticationAccountQuery = authenticationAccountQuery;
        this.redisTuningProperties = redisTuningProperties;
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
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            Optional<SecurityPrincipal> principal = parseCachedPrincipal(cacheKey, cached);
            if (principal.isPresent() && credentialVersionMatches(principal.get(), expectedCredentialVersion)) {
                return principal;
            }
            if (principal.isPresent()) {
                redisTemplate.delete(cacheKey);
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
        return authenticationAccountQuery.findActiveById(userId)
                .map(this::toSnapshot)
                .filter(snapshot -> expectedCredentialVersion == null
                        || snapshot.credentialVersion() == expectedCredentialVersion)
                .map(snapshot -> {
                    writeSnapshot(cacheKey, snapshot);
                    return snapshot.toPrincipal();
                });
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
