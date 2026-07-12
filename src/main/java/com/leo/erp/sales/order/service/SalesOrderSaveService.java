package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;

@Service
public class SalesOrderSaveService {

    private final SalesOrderRepository repository;
    private final SalesOrderPurchaseAllocationService purchaseAllocationService;
    private final SalesOrderCompletionSyncService completionSyncService;
    private final SalesOrderCompletionPolicy completionPolicy;

    public SalesOrderSaveService(SalesOrderRepository repository,
                                 SalesOrderPurchaseAllocationService purchaseAllocationService,
                                 SalesOrderCompletionSyncService completionSyncService,
                                 SalesOrderCompletionPolicy completionPolicy) {
        this.repository = repository;
        this.purchaseAllocationService = purchaseAllocationService;
        this.completionSyncService = completionSyncService;
        this.completionPolicy = completionPolicy;
    }

    SalesOrder save(SalesOrder entity) {
        if (!purchaseAllocationService.hasPurchaseOrderBackedItems(entity)) {
            SalesOrder saved = repository.save(entity);
            syncCompletionAfterAuditedSave(saved);
            return saved;
        }
        SalesOrder saved = repository.saveAndFlush(entity);
        purchaseAllocationService.finalizePurchaseOrderAllocations(saved);
        SalesOrder finalSaved = repository.save(saved);
        syncCompletionAfterAuditedSave(finalSaved);
        return finalSaved;
    }

    SalesOrder saveStatus(SalesOrder entity) {
        SalesOrder saved = repository.save(entity);
        syncCompletionAfterAuditedSave(saved);
        return saved;
    }

    SalesOrder saveAuditedPricingUpdate(SalesOrder entity) {
        SalesOrder saved = repository.save(entity);
        syncCompletionAfterAuditedSave(saved);
        return saved;
    }

    private void syncCompletionAfterAuditedSave(SalesOrder entity) {
        if (completionSyncService == null || !completionPolicy.shouldSyncAfterSave(entity)) {
            return;
        }
        completionSyncService.syncBySalesOrderId(entity.getId());
    }
}
