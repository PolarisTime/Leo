package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheHealthCheckTest {

    private final RedisCacheHealthCheck healthCheck = new RedisCacheHealthCheck() {
        @Override
        public String cacheName() {
            return "test-cache";
        }

        @Override
        public CacheHealthCheckResult verifyAndRefreshCache() {
            return new CacheHealthCheckResult(cacheName(), 0, 0, false);
        }
    };

    @Test
    void shouldTreatNullExpectedListAsEmpty_whenRedisSupportIsMissing() {
        var result = healthCheck.verifyAndRefreshListCache(
                null,
                "test-cache",
                Duration.ofMinutes(5),
                new TypeReference<List<String>>() {},
                null
        );

        assertThat(result.cacheName()).isEqualTo("test-cache");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isFalse();
    }

    @Test
    void shouldRefreshSpringCacheByLogicalCacheNameAndKey() {
        var cacheManager = new ConcurrentMapCacheManager("options");
        var cache = cacheManager.getCache("options");
        cache.put("leo:supplier:all", List.of("stale"));

        var result = healthCheck.verifyAndRefreshSpringCache(
                cacheManager,
                "options",
                "leo:supplier:all",
                List.of("fresh")
        );

        assertThat(cache.get("leo:supplier:all", List.class)).containsExactly("fresh");
        assertThat(result.cacheName()).isEqualTo("options::leo:supplier:all");
        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
    }

    @Test
    void shouldEvictSpringCacheWhenExpectedValueIsMissing() {
        var cacheManager = new ConcurrentMapCacheManager("static");
        var cache = cacheManager.getCache("static");
        cache.put("leo:company:current", "stale");

        var result = healthCheck.verifyAndRefreshSpringCache(
                cacheManager,
                "static",
                "leo:company:current",
                null
        );

        assertThat(cache.get("leo:company:current")).isNull();
        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
    }

    @Test
    void shouldRefreshTypedRedisValueCacheWhenContentDiffers() {
        RedisJsonCacheSupport cache = mock(RedisJsonCacheSupport.class);
        when(cache.read("runtime", String.class)).thenReturn(Optional.of("stale"));

        var result = healthCheck.verifyAndRefreshValueCache(
                cache,
                "runtime",
                Duration.ofMinutes(10),
                String.class,
                "fresh"
        );

        verify(cache).write("runtime", "fresh", Duration.ofMinutes(10));
        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
    }
}
