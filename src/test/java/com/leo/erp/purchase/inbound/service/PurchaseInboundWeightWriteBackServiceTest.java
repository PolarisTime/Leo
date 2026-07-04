package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseInboundWeightWriteBackServiceTest {

    @Test
    void shouldKeepSourcePieceWeightAndRefreshActualWeight() {
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

        assertThat(item.getPieceWeightTon()).isEqualByComparingTo("0.100");
        assertThat(item.getActualPieceWeightTon()).isNull();
        assertThat(item.getActualWeightTon()).isEqualByComparingTo("0.650");
        assertThat(item.getWeightTon()).isEqualByComparingTo("1.000");
        assertThat(item.getAmount()).isEqualByComparingTo("4000.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("1.000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("4000.00");
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

    @Test
    void shouldUsePersistedAccumulatorWhenCurrentAccumulatorMissingAndRegenerateOnlyExistingItems() {
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
        PurchaseOrderItem duplicatedItem = purchaseOrderItem(order, 201L, 10);
        order.getItems().add(item);
        order.getItems().add(duplicatedItem);
        PurchaseInboundItemRepository.PurchaseOrderWeighWeightSummary summary =
                mock(PurchaseInboundItemRepository.PurchaseOrderWeighWeightSummary.class);
        when(summary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(summary.getTotalQuantity()).thenReturn(10L);
        when(summary.getTotalWeightTon()).thenReturn(new BigDecimal("1.250"));
        when(inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L, 999L)),
                eq(1L)
        )).thenReturn(List.of(summary));
        when(purchaseOrderRepository.findByIdInAndDeletedFlagFalse(List.of(301L))).thenReturn(List.of(order));

        service.writeBackPurchaseOrderWeights(
                List.of(201L, 999L),
                1L,
                Map.of(),
                Map.of(201L, item)
        );

        assertThat(item.getWeightTon()).isEqualByComparingTo("1.250");
        assertThat(item.getActualWeightTon()).isEqualByComparingTo("1.250");
        assertThat(item.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("2.250");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("9000.00");
        verify(purchaseOrderRepository).saveAll(any());
        verify(pieceWeightService).regenerateForPurchaseOrderItems(List.of(item));
    }

    @Test
    void shouldClearActualWeightWhenWeighAccumulatorHasNoQuantity() {
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
                Map.of(201L, new PurchaseInboundWeightWriteBackService.SourceWeighAccumulator(
                        0,
                        BigDecimal.ZERO
                )),
                Map.of(201L, item)
        );

        assertThat(item.getActualWeightTon()).isNull();
        assertThat(item.getActualPieceWeightTon()).isNull();
        assertThat(item.getWeightTon()).isEqualByComparingTo("1.000");
        assertThat(item.getAmount()).isEqualByComparingTo("4000.00");
        verify(purchaseOrderRepository).saveAll(any());
        verify(pieceWeightService).regenerateForPurchaseOrderItems(List.of(item));
    }

    @Test
    void shouldSkipWriteBackWhenSourceItemsHaveNoPersistedOrderIds() {
        PurchaseInboundItemRepository inboundItemRepository = mock(PurchaseInboundItemRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        PurchaseOrderItemPieceWeightService pieceWeightService = mock(PurchaseOrderItemPieceWeightService.class);
        PurchaseInboundWeightWriteBackService service = new PurchaseInboundWeightWriteBackService(
                inboundItemRepository,
                purchaseOrderRepository,
                pieceWeightService
        );

        PurchaseOrderItem itemWithoutOrder = purchaseOrderItem(null, 201L, 10);
        PurchaseOrder unsavedOrder = new PurchaseOrder();
        PurchaseOrderItem itemWithoutOrderId = purchaseOrderItem(unsavedOrder, 202L, 10);
        Map<Long, PurchaseOrderItem> sourceItemMap = new java.util.LinkedHashMap<>();
        sourceItemMap.put(201L, itemWithoutOrder);
        sourceItemMap.put(202L, itemWithoutOrderId);
        when(inboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                eq(List.of(201L, 202L)),
                eq(1L)
        )).thenReturn(List.of());

        service.writeBackPurchaseOrderWeights(
                List.of(201L, 202L),
                1L,
                Map.of(),
                sourceItemMap
        );

        assertThat(itemWithoutOrder.getActualWeightTon()).isNull();
        assertThat(itemWithoutOrderId.getActualWeightTon()).isNull();
        verifyNoInteractions(purchaseOrderRepository, pieceWeightService);
    }

    @Test
    void shouldReturnEmptyPersistedAccumulatorMapWhenSourceIdsEmpty() {
        PurchaseInboundWeightWriteBackService service = new PurchaseInboundWeightWriteBackService(
                mock(PurchaseInboundItemRepository.class),
                mock(PurchaseOrderRepository.class),
                mock(PurchaseOrderItemPieceWeightService.class)
        );

        Object result = ReflectionTestUtils.invokeMethod(
                service,
                "loadPersistedWeighAccumulatorMap",
                List.of(),
                1L
        );

        assertThat(result).isEqualTo(Map.of());
    }

    @Test
    void shouldEvaluateAccumulatorQuantityBoundaries() {
        PurchaseInboundWeightWriteBackService.SourceWeighAccumulator nullQuantity =
                new PurchaseInboundWeightWriteBackService.SourceWeighAccumulator(null, BigDecimal.ZERO);
        PurchaseInboundWeightWriteBackService.SourceWeighAccumulator zeroQuantity =
                new PurchaseInboundWeightWriteBackService.SourceWeighAccumulator(0, BigDecimal.ZERO);
        PurchaseInboundWeightWriteBackService.SourceWeighAccumulator enoughQuantity =
                new PurchaseInboundWeightWriteBackService.SourceWeighAccumulator(3, new BigDecimal("0.300"));

        assertThat(nullQuantity.hasQuantity()).isFalse();
        assertThat(zeroQuantity.hasQuantity()).isFalse();
        assertThat(enoughQuantity.hasQuantity()).isTrue();
        assertThat(enoughQuantity.isFullyAllocated(null)).isFalse();
        assertThat(nullQuantity.isFullyAllocated(1)).isFalse();
        assertThat(enoughQuantity.isFullyAllocated(3)).isTrue();
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
