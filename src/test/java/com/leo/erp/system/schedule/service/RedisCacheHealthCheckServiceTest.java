package com.leo.erp.system.schedule.service;

import com.leo.erp.common.support.RedisCacheHealthCheck;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheHealthCheckServiceTest {

    @Test
    void shouldAllowNullHealthChecks() {
        var service = new RedisCacheHealthCheckService(null);

        assertThatCode(service::verifyAndRefreshCaches).doesNotThrowAnyException();
    }

    @Test
    void shouldVerifyAllRegisteredCaches() {
        RedisCacheHealthCheck first = mock(RedisCacheHealthCheck.class);
        RedisCacheHealthCheck second = mock(RedisCacheHealthCheck.class);
        when(first.cacheName()).thenReturn("first");
        when(second.cacheName()).thenReturn("second");
        when(first.verifyAndRefreshCache())
                .thenReturn(new RedisCacheHealthCheck.CacheHealthCheckResult("first", 1, 1, false));
        when(second.verifyAndRefreshCache())
                .thenReturn(new RedisCacheHealthCheck.CacheHealthCheckResult("second", 1, 1, false));
        var service = new RedisCacheHealthCheckService(List.of(first, second));

        service.verifyAndRefreshCaches();

        verify(first).verifyAndRefreshCache();
        verify(second).verifyAndRefreshCache();
    }

    @Test
    void shouldContinueWhenOneCacheCheckFails() {
        RedisCacheHealthCheck first = mock(RedisCacheHealthCheck.class);
        RedisCacheHealthCheck second = mock(RedisCacheHealthCheck.class);
        when(first.cacheName()).thenReturn("first");
        when(second.cacheName()).thenReturn("second");
        doThrow(new RuntimeException("boom")).when(first).verifyAndRefreshCache();
        when(second.verifyAndRefreshCache())
                .thenReturn(new RedisCacheHealthCheck.CacheHealthCheckResult("second", 1, 1, false));
        var service = new RedisCacheHealthCheckService(List.of(first, second));

        service.verifyAndRefreshCaches();

        verify(second).verifyAndRefreshCache();
    }

    @Test
    void shouldIgnoreNullHealthCheckResult() {
        RedisCacheHealthCheck healthCheck = mock(RedisCacheHealthCheck.class);
        when(healthCheck.cacheName()).thenReturn("first");
        when(healthCheck.verifyAndRefreshCache()).thenReturn(null);
        var service = new RedisCacheHealthCheckService(List.of(healthCheck));

        service.verifyAndRefreshCaches();

        verify(healthCheck).verifyAndRefreshCache();
    }

    @Test
    void shouldHandleRefreshedHealthCheckResult() {
        RedisCacheHealthCheck healthCheck = mock(RedisCacheHealthCheck.class);
        when(healthCheck.cacheName()).thenReturn("first");
        when(healthCheck.verifyAndRefreshCache())
                .thenReturn(new RedisCacheHealthCheck.CacheHealthCheckResult("first", 1, 2, true));
        var service = new RedisCacheHealthCheckService(List.of(healthCheck));

        service.verifyAndRefreshCaches();

        verify(healthCheck).verifyAndRefreshCache();
    }

    @Test
    void shouldTreatNullExpectedValuesAsEmptyWhenVerifyingListCache() {
        RedisCacheHealthCheck healthCheck = defaultHealthCheck();

        var result = healthCheck.verifyAndRefreshListCache(
                null,
                "cache:key",
                Duration.ofMinutes(5),
                STRING_LIST_TYPE_REFERENCE,
                null
        );

        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isFalse();
    }

    @Test
    void shouldDeleteExistingCacheWhenExpectedValuesAreEmpty() {
        RedisCacheHealthCheck healthCheck = defaultHealthCheck();
        RedisJsonCacheSupport redisSupport = mock(RedisJsonCacheSupport.class);
        when(redisSupport.read("cache:key", STRING_LIST_TYPE_REFERENCE)).thenReturn(Optional.of(List.of("stale")));

        var result = healthCheck.verifyAndRefreshListCache(
                redisSupport,
                "cache:key",
                Duration.ofMinutes(5),
                STRING_LIST_TYPE_REFERENCE,
                List.of()
        );

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
        verify(redisSupport).delete("cache:key");
    }

    private RedisCacheHealthCheck defaultHealthCheck() {
        return new TestRedisCacheHealthCheck();
    }

    private static final TypeReference<List<String>> STRING_LIST_TYPE_REFERENCE = new StringListTypeReference();

    private static final class StringListTypeReference extends TypeReference<List<String>> {
    }

    private static final class TestRedisCacheHealthCheck implements RedisCacheHealthCheck {
        @Override
        public String cacheName() {
            return "cache:key";
        }

        @Override
        public CacheHealthCheckResult verifyAndRefreshCache() {
            return new CacheHealthCheckResult("cache:key", 0, 0, false);
        }
    }
}
