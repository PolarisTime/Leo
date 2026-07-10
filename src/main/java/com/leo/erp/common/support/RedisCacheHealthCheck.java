package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface RedisCacheHealthCheck {

    String cacheName();

    CacheHealthCheckResult verifyAndRefreshCache();

    default CacheHealthCheckResult verifyAndRefreshSpringCache(CacheManager cacheManager,
                                                               String cacheName,
                                                               String cacheKey,
                                                               Object expected) {
        String qualifiedCacheName = cacheName + "::" + cacheKey;
        if (cacheManager == null) {
            int expectedSize = valueSize(expected);
            return new CacheHealthCheckResult(qualifiedCacheName, expectedSize, expectedSize, false);
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            int expectedSize = valueSize(expected);
            return new CacheHealthCheckResult(qualifiedCacheName, expectedSize, expectedSize, false);
        }

        Cache.ValueWrapper wrapper = cache.get(cacheKey);
        Object current = wrapper == null ? null : wrapper.get();
        int currentSize = valueSize(current);
        int expectedSize = valueSize(expected);
        if (expected == null) {
            if (wrapper != null) {
                cache.evict(cacheKey);
                return new CacheHealthCheckResult(qualifiedCacheName, currentSize, 0, true);
            }
            return new CacheHealthCheckResult(qualifiedCacheName, 0, 0, false);
        }
        if (!Objects.equals(current, expected)) {
            cache.put(cacheKey, expected);
            return new CacheHealthCheckResult(qualifiedCacheName, currentSize, expectedSize, true);
        }
        return new CacheHealthCheckResult(qualifiedCacheName, currentSize, expectedSize, false);
    }

    default <T> CacheHealthCheckResult verifyAndRefreshValueCache(RedisJsonCacheSupport redisJsonCacheSupport,
                                                                  String cacheKey,
                                                                  Duration ttl,
                                                                  Class<T> type,
                                                                  T expected) {
        int expectedSize = valueSize(expected);
        if (redisJsonCacheSupport == null) {
            return new CacheHealthCheckResult(cacheKey, expectedSize, expectedSize, false);
        }
        Optional<T> cachedValue = redisJsonCacheSupport.read(cacheKey, type);
        T current = cachedValue.orElse(null);
        int currentSize = valueSize(current);
        if (expected == null) {
            if (cachedValue.isPresent()) {
                redisJsonCacheSupport.delete(cacheKey);
                return new CacheHealthCheckResult(cacheKey, currentSize, 0, true);
            }
            return new CacheHealthCheckResult(cacheKey, 0, 0, false);
        }
        if (!Objects.equals(current, expected)) {
            redisJsonCacheSupport.write(cacheKey, expected, ttl);
            return new CacheHealthCheckResult(cacheKey, currentSize, expectedSize, true);
        }
        return new CacheHealthCheckResult(cacheKey, currentSize, expectedSize, false);
    }

    default <T> CacheHealthCheckResult verifyAndRefreshListCache(RedisJsonCacheSupport redisJsonCacheSupport,
                                                                 String cacheKey,
                                                                 Duration ttl,
                                                                 TypeReference<List<T>> typeReference,
                                                                 List<T> expected) {
        List<T> expectedValues = expected == null ? List.of() : expected;
        if (redisJsonCacheSupport == null) {
            return new CacheHealthCheckResult(cacheKey, expectedValues.size(), expectedValues.size(), false);
        }
        Optional<List<T>> cachedValues = redisJsonCacheSupport.read(cacheKey, typeReference);
        List<T> currentValues = cachedValues.orElse(List.of());
        if (expectedValues.isEmpty()) {
            if (cachedValues.isPresent()) {
                redisJsonCacheSupport.delete(cacheKey);
                return new CacheHealthCheckResult(cacheKey, currentValues.size(), 0, true);
            }
            return new CacheHealthCheckResult(cacheKey, currentValues.size(), 0, false);
        }
        if (!Objects.equals(currentValues, expectedValues)) {
            redisJsonCacheSupport.write(cacheKey, expectedValues, ttl);
            return new CacheHealthCheckResult(cacheKey, currentValues.size(), expectedValues.size(), true);
        }
        return new CacheHealthCheckResult(cacheKey, currentValues.size(), expectedValues.size(), false);
    }

    record CacheHealthCheckResult(String cacheName, int currentSize, int refreshedSize, boolean refreshed) {
    }

    private static int valueSize(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
    }
}
