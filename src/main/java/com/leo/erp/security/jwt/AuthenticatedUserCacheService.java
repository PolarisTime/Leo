package com.leo.erp.security.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.security.support.SecurityPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuthenticatedUserCacheService {

    private static final String USER_CACHE_PREFIX = "auth:user:snapshot:";
    private static final String USER_CACHE_INDEX_KEY = "auth:user:snapshot:index";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingService userRoleBindingService;
    private final RedisTuningProperties redisTuningProperties;

    @Autowired
    public AuthenticatedUserCacheService(StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         UserAccountRepository userAccountRepository,
                                         UserRoleBindingService userRoleBindingService,
                                         RedisTuningProperties redisTuningProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingService = userRoleBindingService;
        this.redisTuningProperties = redisTuningProperties;
    }

    public Optional<SecurityPrincipal> getActivePrincipal(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        String cacheKey = cacheKey(userId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            Optional<SecurityPrincipal> principal = parseCachedPrincipal(cacheKey, cached);
            if (principal.isPresent()) {
                return principal;
            }
        }

        return loadAndCachePrincipal(userId, cacheKey);
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

    private Optional<SecurityPrincipal> loadAndCachePrincipal(Long userId, String cacheKey) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .filter(user -> user.getStatus() == UserStatus.NORMAL)
                .map(this::toSnapshot)
                .map(snapshot -> {
                    writeSnapshot(cacheKey, snapshot);
                    return snapshot.toPrincipal();
                });
    }

    private CachedAuthenticatedUser toSnapshot(UserAccount user) {
        Collection<? extends GrantedAuthority> authorities = userRoleBindingService.toGrantedAuthorities(
                userRoleBindingService.resolveRolesForUser(user.getId())
        );
        List<String> authorityValues = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .distinct()
                .toList();
        return new CachedAuthenticatedUser(
                user.getId(),
                user.getLoginName(),
                authorityValues,
                Boolean.TRUE.equals(user.getTotpEnabled()),
                Boolean.TRUE.equals(user.getRequireTotpSetup())
        );
    }

    private void writeSnapshot(String cacheKey, CachedAuthenticatedUser snapshot) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(snapshot),
                    redisTuningProperties.withTtlJitter(redisTuningProperties.authUserTtl())
            );
            redisTemplate.opsForSet().add(USER_CACHE_INDEX_KEY, String.valueOf(snapshot.userId()));
            redisTemplate.expire(USER_CACHE_INDEX_KEY, redisTuningProperties.authUserIndexTtl());
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
            List<String> authorities,
            boolean totpEnabled,
            boolean forceTotpSetup
    ) {

        private SecurityPrincipal toPrincipal() {
            return SecurityPrincipal.authenticated(
                    userId,
                    loginName,
                    authorities.stream()
                            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                            .toList(),
                    totpEnabled,
                    forceTotpSetup
            );
        }
    }
}
