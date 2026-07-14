package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.sales.order.service.SalesOrderAllocatedWeightSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderPlanWeightSyncServiceTest {

    @Test
    void shouldRebalancePiecesBeforeSyncingDownstreamWeights() {
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        SalesOrderAllocatedWeightSyncService salesOrderSyncService = mock(SalesOrderAllocatedWeightSyncService.class);
        PurchaseOrderPlanWeightSyncService service =
                new PurchaseOrderPlanWeightSyncService(pieceWeightService, salesOrderSyncService);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);

        when(salesOrderSyncService.findLockedSalesOrderItemIdsByPurchaseOrderItemIds(List.of(201L)))
                .thenReturn(Set.of(301L));

        service.syncAfterPurchaseOrderWeightWriteBack(List.of(item));

        var inOrder = inOrder(pieceWeightService, salesOrderSyncService);
        inOrder.verify(salesOrderSyncService).findLockedSalesOrderItemIdsByPurchaseOrderItemIds(List.of(201L));
        inOrder.verify(pieceWeightService).rebalanceForPurchaseOrderItems(List.of(item), Set.of(301L));
        inOrder.verify(salesOrderSyncService).syncByPurchaseOrderItemIds(List.of(201L), Set.of(301L));
    }
}
