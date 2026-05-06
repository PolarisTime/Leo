package com.leo.erp.security.permission;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Component
class PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);

    static final String CACHE_PREFIX = "leo:resource-perm:";
    static final String SCOPE_CACHE_PREFIX = "leo:resource-scope:";
    static final long CACHE_TTL_MINUTES = 5;
    private static final int SCAN_BATCH_SIZE = 256;
    private static final int MAX_SCAN_KEYS = 10000;

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    PermissionCache(StringRedisTemplate redisTemplate, Optional<RedisJsonCacheSupport> redisJsonCacheSupport) {
        this.redisTemplate = redisTemplate;
        this.redisJsonCacheSupport = redisJsonCacheSupport == null ? null : redisJsonCacheSupport.orElse(null);
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
        redisTemplate.expire(CACHE_PREFIX + userId, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        if (!snapshot.dataScopeByPermission().isEmpty()) {
            redisTemplate.opsForHash().putAll(SCOPE_CACHE_PREFIX + userId, snapshot.dataScopeByPermission());
            redisTemplate.expire(SCOPE_CACHE_PREFIX + userId, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    void evict(Long userId) {
        redisTemplate.delete(List.of(CACHE_PREFIX + userId, SCOPE_CACHE_PREFIX + userId));
    }

    void evictMetadata(String menuCacheKey) {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.delete(List.of(menuCacheKey));
    }

    void evictAll() {
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
        List<String> batch = new ArrayList<>(256);
        try {
            scanAndDelete(connection, CACHE_PREFIX + "*", batch);
            scanAndDelete(connection, SCOPE_CACHE_PREFIX + "*", batch);
        } finally {
            connection.close();
        }
    }

    private void scanAndDelete(RedisConnection connection, String pattern, List<String> batch) {
        batch.clear();
        int totalDeleted = 0;
        try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build())) {
            while (cursor.hasNext()) {
                batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                if (batch.size() >= SCAN_BATCH_SIZE) {
                    redisTemplate.delete(batch);
                    totalDeleted += batch.size();
                    batch.clear();
                    if (totalDeleted >= MAX_SCAN_KEYS) {
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
