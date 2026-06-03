package com.leo.erp.system.dashboard.service;

import com.leo.erp.auth.service.SessionInvalidatedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DashboardCacheEvictListenerTest {

    @Test
    void shouldEvictCache_whenUserIdNotNull() {
        var dashboardSummaryService = mock(DashboardSummaryService.class);
        var listener = new DashboardCacheEvictListener(dashboardSummaryService);
        var event = new SessionInvalidatedEvent(1L, "token-1", true);

        listener.onSessionInvalidated(event);

        verify(dashboardSummaryService).evictCache(1L);
    }

    @Test
    void shouldNotEvictCache_whenUserIdNull() {
        var dashboardSummaryService = mock(DashboardSummaryService.class);
        var listener = new DashboardCacheEvictListener(dashboardSummaryService);
        var event = new SessionInvalidatedEvent(null, "token-1", true);

        listener.onSessionInvalidated(event);

        verify(dashboardSummaryService, never()).evictCache(any());
    }
}
