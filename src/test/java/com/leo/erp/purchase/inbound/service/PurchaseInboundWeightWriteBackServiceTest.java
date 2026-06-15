package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundWeightWriteBackServiceTest {

    @Test
    void shouldWriteBackAverageActualWeightAndRefreshPurchaseOrderTotals() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseInboundWeightWriteBackService service = new PurchaseInboundWeightWriteBackService(
                inboundItemRepository,
                purchaseOrderRepository,
                pieceWeightService
        );

        PurchaseOrder order = purchaseOrder();
        PurchaseOrderItem item = purchaseOrderItem(order, 201L, 10);
        order.getItems().add(item);
        PurchaseInboundItemRepository.PurchaseOrderWeighWeightSummary summary =
                mock(PurchaseInboundItemRepository.PurchaseOrderWeighWeightSummary.class);
        when(summary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(summary.getTotalQuantity()).thenReturn(4L);
        when(summary.getTotalWeightTon()).thenReturn(new BigDecimal("0.430"));
        when(inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                eq(1L)
        )).thenReturn(List.of(summary));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(order));

        service.writeBackPurchaseOrderWeights(
                List.of(201L),
                1L,
                Map.of(201L, new PurchaseInboundWeightWriteBackService.SourceWeighAccumulator(
                        2,
                        new BigDecimal("0.220")
                )),
                Map.of(201L, item)
        );

        assertThat(item.getActualPieceWeightTon()).isEqualByComparingTo("0.108");
        assertThat(item.getActualWeightTon()).isEqualByComparingTo("1.080");
        assertThat(item.getWeightTon()).isEqualByComparingTo("1.080");
        assertThat(item.getAmount()).isEqualByComparingTo("4320.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("1.080");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("4320.00");
        verify(purchaseOrderRepository).saveAll(any());
        verify(pieceWeightService).regenerateForPurchaseOrderItems(List.of(item));
    }

    @Test
    void shouldClearActualWeightWhenNoWeighAccumulatorExists() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseInboundWeightWriteBackService service = new PurchaseInboundWeightWriteBackService(
                inboundItemRepository,
                purchaseOrderRepository,
                pieceWeightService
        );

        PurchaseOrder order = purchaseOrder();
        PurchaseOrderItem item = purchaseOrderItem(order, 201L, 10);
        item.setActualWeightTon(new BigDecimal("1.000"));
        item.setActualPieceWeightTon(new BigDecimal("0.100"));
        order.getItems().add(item);
        when(inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L)),
                eq(1L)
        )).thenReturn(List.of());
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(order));

        service.writeBackPurchaseOrderWeights(
                List.of(201L),
                1L,
                Map.of(),
                Map.of(201L, item)
        );

        assertThat(item.getActualWeightTon()).isNull();
        assertThat(item.getActualPieceWeightTon()).isNull();
        verify(purchaseOrderRepository).saveAll(any());
        verify(pieceWeightService).regenerateForPurchaseOrderItems(List.of(item));
    }

    private PurchaseOrder purchaseOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(301L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        return order;
    }

    private PurchaseOrderItem purchaseOrderItem(PurchaseOrder order, Long itemId, Integer quantity) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(itemId);
        item.setPurchaseOrder(order);
        item.setQuantity(quantity);
        item.setPieceWeightTon(new BigDecimal("0.100"));
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("4000.00"));
        return item;
    }
}
