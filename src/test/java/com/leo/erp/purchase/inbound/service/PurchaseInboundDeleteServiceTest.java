package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundDeleteServiceTest {

    @Test
    void shouldWriteBackSourcePurchaseOrderWeightsBeforeDelete() {
        PurchaseOrderItemQueryService itemQueryService = mock(PurchaseOrderItemQueryService.class);
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseInboundSourceValidator sourceValidator = new PurchaseInboundSourceValidator(
                itemQueryService,
                new PurchaseInboundAllocationService(inboundItemRepository)
        );
        PurchaseInboundDeleteService service = new PurchaseInboundDeleteService(
                sourceValidator,
                new PurchaseInboundWeightWriteBackService(
                        inboundItemRepository,
                        purchaseOrderRepository,
                        pieceWeightService
                )
        );

        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.getItems().add(inboundItem(101L, 201L));
        inbound.getItems().add(inboundItem(102L, 201L));

        PurchaseOrder order = new PurchaseOrder();
        order.setId(301L);
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItem(order);
        order.getItems().add(sourceItem);

        when(itemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                eq(1L)
        )).thenReturn(List.of());
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(order));

        service.beforeDelete(inbound);

        verify(itemQueryService).findActiveByIdIn(List.of(201L));
        verify(purchaseOrderRepository).saveAll(any());
        verify(pieceWeightService).regenerateForPurchaseOrderItems(List.of(sourceItem));
    }

    private PurchaseInboundItem inboundItem(Long id, Long sourcePurchaseOrderItemId) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        item.setSourcePurchaseOrderItemId(sourcePurchaseOrderItemId);
        return item;
    }

    private PurchaseOrderItem sourcePurchaseOrderItem(PurchaseOrder order) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setPurchaseOrder(order);
        item.setQuantity(10);
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4000.00"));
        return item;
    }
}
