package com.leo.erp.security.permission;

import lombok.extern.slf4j.Slf4j;
import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
class PermissionCache {
    static final String CACHE_PREFIX = "leo:resource-perm:";
    static final String SCOPE_CACHE_PREFIX = "leo:resource-scope:";
    private static final String USER_INDEX_KEY = "leo:resource-perm:index:users";

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final RedisTuningProperties redisTuningProperties;

    PermissionCache(StringRedisTemplate redisTemplate,
                    Optional<RedisJsonCacheSupport> redisJsonCacheSupport,
                    RedisTuningProperties redisTuningProperties) {
        this.redisTemplate = redisTemplate;
        this.redisJsonCacheSupport = redisJsonCacheSupport == null ? null : redisJsonCacheSupport.orElse(null);
        this.redisTuningProperties = redisTuningProperties;
    }

    UserPermissionSnapshot read(Long userId) {
        String permissionCacheKey = CACHE_PREFIX + userId;
        String scopeCacheKey = SCOPE_CACHE_PREFIX + userId;
        Map<Object, Object> cachedPermissions = redisTemplate.opsForHash().entries(permissionCacheKey);
        Map<Object, Object> cachedScopes = redisTemplate.opsForHash().entries(scopeCacheKey);
        if (cachedPermissions.isEmpty() || cachedScopes.isEmpty()) {
            return null;
        }
        Map<String, Set<String>> permissionMap = new LinkedHashMap<>();
        cachedPermissions.forEach((key, value) -> permissionMap.put(
                ResourcePermissionCatalog.normalizeResource(key.toString()),
                splitActions(value.toString())
        ));
        Map<String, String> dataScopeByPermission = new LinkedHashMap<>();
        cachedScopes.forEach((key, value) -> {
            String normalizedKey = PermissionScopeKeyParser.normalize(key.toString());
            if (!normalizedKey.isBlank()) {
                dataScopeByPermission.put(normalizedKey, ResourcePermissionCatalog.normalizeDataScope(value.toString()));
            }
        });
        return new UserPermissionSnapshot(permissionMap, dataScopeByPermission);
    }

    void write(Long userId, UserPermissionSnapshot snapshot) {
        if (snapshot.permissionMap().isEmpty()) {
            return;
        }
        Map<String, String> permissionCacheData = new LinkedHashMap<>();
        snapshot.permissionMap().forEach((resource, actions) -> permissionCacheData.put(resource, String.join(",", actions)));
        redisTemplate.opsForHash().putAll(CACHE_PREFIX + userId, permissionCacheData);
        redisTemplate.expire(CACHE_PREFIX + userId, redisTuningProperties.permissionTtl().toSeconds(), TimeUnit.SECONDS);
        if (!snapshot.dataScopeByPermission().isEmpty()) {
            redisTemplate.opsForHash().putAll(SCOPE_CACHE_PREFIX + userId, snapshot.dataScopeByPermission());
            redisTemplate.expire(SCOPE_CACHE_PREFIX + userId, redisTuningProperties.permissionTtl().toSeconds(), TimeUnit.SECONDS);
        }
        redisTemplate.opsForSet().add(USER_INDEX_KEY, String.valueOf(userId));
        redisTemplate.expire(USER_INDEX_KEY, redisTuningProperties.permissionIndexTtl().toSeconds(), TimeUnit.SECONDS);
    }

    void evict(Long userId) {
        redisTemplate.delete(List.of(CACHE_PREFIX + userId, SCOPE_CACHE_PREFIX + userId));
        redisTemplate.opsForSet().remove(USER_INDEX_KEY, String.valueOf(userId));
    }

    void evictMetadata(String menuCacheKey) {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.delete(List.of(menuCacheKey));
    }

    void evictAll() {
        if (evictIndexedUsers()) {
            return;
        }
        log.warn("Permission cache user index unavailable, falling back to bounded SCAN eviction");
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.deleteByPattern(CACHE_PREFIX + "*");
            redisJsonCacheSupport.deleteByPattern(SCOPE_CACHE_PREFIX + "*");
            return;
        }
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }
        RedisConnection connection = connectionFactory.getConnection();
        List<String> batch = new ArrayList<>(redisTuningProperties.deleteBatchSize());
        try {
            scanAndDelete(connection, CACHE_PREFIX + "*", batch);
            scanAndDelete(connection, SCOPE_CACHE_PREFIX + "*", batch);
        } finally {
            connection.close();
        }
    }

    private boolean evictIndexedUsers() {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(USER_INDEX_KEY))) {
            return false;
        }
        int deleted = 0;
        List<String> batch = new ArrayList<>(redisTuningProperties.deleteBatchSize() * 2);
        try (Cursor<String> cursor = redisTemplate.opsForSet().scan(
                USER_INDEX_KEY,
                ScanOptions.scanOptions().count(redisTuningProperties.scanBatchSize()).build()
        )) {
            while (cursor.hasNext()) {
                String userId = cursor.next();
                batch.add(CACHE_PREFIX + userId);
                batch.add(SCOPE_CACHE_PREFIX + userId);
                if (batch.size() >= redisTuningProperties.deleteBatchSize()) {
                    redisTemplate.delete(batch);
                    deleted += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
                deleted += batch.size();
            }
            redisTemplate.delete(USER_INDEX_KEY);
            if (deleted > 0) {
                log.info("Permission cache indexed eviction completed, keys={}", deleted);
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("Permission cache indexed eviction failed", ex);
            return false;
        }
    }

    private void scanAndDelete(RedisConnection connection, String pattern, List<String> batch) {
        batch.clear();
        int totalDeleted = 0;
        try (Cursor<byte[]> cursor = connection.scan(
                ScanOptions.scanOptions().match(pattern).count(redisTuningProperties.scanBatchSize()).build()
        )) {
            while (cursor.hasNext()) {
                batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                if (batch.size() >= redisTuningProperties.deleteBatchSize()) {
                    redisTemplate.delete(batch);
                    totalDeleted += batch.size();
                    batch.clear();
                    if (totalDeleted >= redisTuningProperties.maxScanKeys()) {
                        log.warn("Permission cache scan reached max limit, pattern={}, deleted={}", pattern, totalDeleted);
                        break;
                    }
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
                totalDeleted += batch.size();
            }
            if (totalDeleted > 0) {
                log.info("Permission cache scan delete completed, pattern={}, deleted={}", pattern, totalDeleted);
            }
        }
    }

    Set<String> splitActions(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> actions = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String action = ResourcePermissionCatalog.normalizeAction(item);
            if (!action.isBlank()) {
                actions.add(action);
            }
        }
        return actions;
    }
}
