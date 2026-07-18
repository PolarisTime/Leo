package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;

@Service
public class SalesOrderSaveService {

    private final SalesOrderRepository repository;
    private final SalesOrderCompletionSyncService completionSyncService;
    private final SalesOrderCompletionPolicy completionPolicy;

    public SalesOrderSaveService(SalesOrderRepository repository,
                                 SalesOrderCompletionSyncService completionSyncService,
                                 SalesOrderCompletionPolicy completionPolicy) {
        this.repository = repository;
        this.completionSyncService = completionSyncService;
        this.completionPolicy = completionPolicy;
    }

    SalesOrder save(SalesOrder entity) {
        SalesOrder saved = repository.saveAndFlush(entity);
        syncCompletionAfterAuditedSave(saved);
        return saved;
    }

    SalesOrder saveStatus(SalesOrder entity) {
        SalesOrder saved = repository.save(entity);
        syncCompletionAfterAuditedSave(saved);
        return saved;
    }

    SalesOrder saveAuditedPricingUpdate(SalesOrder entity) {
        return repository.save(entity);
    }

    private void syncCompletionAfterAuditedSave(SalesOrder entity) {
        if (completionSyncService == null || !completionPolicy.shouldSyncAfterSave(entity)) {
            return;
        }
        completionSyncService.syncBySalesOrderId(entity.getId());
    }
}
