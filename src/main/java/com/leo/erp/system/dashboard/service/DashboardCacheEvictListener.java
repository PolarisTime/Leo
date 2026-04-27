package com.leo.erp.system.dashboard.service;

import com.leo.erp.auth.service.SessionInvalidatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DashboardCacheEvictListener {

    private final DashboardSummaryService dashboardSummaryService;

    public DashboardCacheEvictListener(DashboardSummaryService dashboardSummaryService) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @EventListener
    public void onSessionInvalidated(SessionInvalidatedEvent event) {
        if (event.userId() != null) {
            dashboardSummaryService.evictCache(event.userId());
        }
    }
}
