package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface RedisCacheHealthCheck {

    String cacheName();

    CacheHealthCheckResult verifyAndRefreshCache();

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
}
