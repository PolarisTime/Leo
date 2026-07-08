package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.sales.order.service.SalesOrderAllocatedWeightSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PurchaseOrderPlanWeightSyncService {

    private final PurchaseOrderItemPieceWeightService pieceWeightService;
    private final SalesOrderAllocatedWeightSyncService salesOrderWeightSyncService;

    public PurchaseOrderPlanWeightSyncService(PurchaseOrderItemPieceWeightService pieceWeightService,
                                              SalesOrderAllocatedWeightSyncService salesOrderWeightSyncService) {
        this.pieceWeightService = pieceWeightService;
        this.salesOrderWeightSyncService = salesOrderWeightSyncService;
    }

    @Transactional
    public void syncAfterPurchaseOrderWeightWriteBack(Collection<PurchaseOrderItem> sourceItems) {
        List<PurchaseOrderItem> items = sourceItems == null
                ? List.of()
                : sourceItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .toList();
        if (items.isEmpty()) {
            return;
        }
        List<Long> sourceItemIds = items.stream()
                .map(PurchaseOrderItem::getId)
                .distinct()
                .toList();
        Set<Long> lockedSalesOrderItemIds =
                salesOrderWeightSyncService.findLockedSalesOrderItemIdsByPurchaseOrderItemIds(sourceItemIds);
        pieceWeightService.rebalanceForPurchaseOrderItems(items, lockedSalesOrderItemIds);
        salesOrderWeightSyncService.syncByPurchaseOrderItemIds(sourceItemIds, lockedSalesOrderItemIds);
    }
}
