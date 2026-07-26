package com.leo.erp.system.dashboard.service;

import com.leo.erp.auth.api.SessionInvalidatedEvent;
import com.leo.erp.auth.api.UserAccountChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserAccountChanged(UserAccountChangedEvent event) {
        if (event.userId() != null) {
            dashboardSummaryService.evictCache(event.userId());
        }
    }
}
