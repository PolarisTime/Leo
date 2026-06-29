package com.leo.erp.system.schedule.service;

import com.leo.erp.common.support.RedisCacheHealthCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheHealthCheckServiceTest {

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
}
