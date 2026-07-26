package com.leo.erp.sales.order.service;

import com.leo.erp.purchase.api.PurchaseOrderReferenceGuard;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class SalesOrderPurchaseReferenceAdapter implements PurchaseOrderReferenceGuard {

    private final SalesOrderItemRepository salesOrderItemRepository;

    public SalesOrderPurchaseReferenceAdapter(SalesOrderItemRepository salesOrderItemRepository) {
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    @Override
    public boolean hasActivePurchaseOrderItemReferences(Collection<Long> purchaseOrderItemIds) {
        return !salesOrderItemRepository
                .findActiveBySourcePurchaseOrderItemIds(purchaseOrderItemIds)
                .isEmpty();
    }

    @Override
    public boolean hasActiveInboundItemReferences(Collection<Long> inboundItemIds) {
        return !salesOrderItemRepository.findActiveBySourceInboundItemIds(inboundItemIds).isEmpty();
    }
}
