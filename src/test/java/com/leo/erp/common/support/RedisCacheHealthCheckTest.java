package com.leo.erp.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
