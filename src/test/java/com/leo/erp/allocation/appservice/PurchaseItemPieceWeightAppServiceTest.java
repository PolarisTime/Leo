package com.leo.erp.allocation.appservice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PurchaseItemPieceWeightAppServiceTest {

    @Test
    void shouldAllocateForSalesOrderItem() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Long sourcePurchaseOrderItemId = 1L;
        Integer quantity = 100;
        Long salesOrderItemId = 2L;
        int lineNo = 1;
        BigDecimal expectedWeight = new BigDecimal("5.5");
        when(service.allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo))
                .thenReturn(expectedWeight);

        BigDecimal result = service.allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo);

        assertThat(result).isEqualByComparingTo(expectedWeight);
        verify(service).allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo);
    }

    @Test
    void shouldReturnZeroWeightWhenNoAllocation() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Long sourcePurchaseOrderItemId = 1L;
        Integer quantity = 0;
        Long salesOrderItemId = 2L;
        int lineNo = 1;
        when(service.allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal result = service.allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldHandleLargeQuantityAllocation() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Long sourcePurchaseOrderItemId = 10L;
        Integer quantity = 10000;
        Long salesOrderItemId = 20L;
        int lineNo = 5;
        BigDecimal expectedWeight = new BigDecimal("1234.567");
        when(service.allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo))
                .thenReturn(expectedWeight);

        BigDecimal result = service.allocateForSalesOrderItem(sourcePurchaseOrderItemId, quantity, salesOrderItemId, lineNo);

        assertThat(result).isEqualByComparingTo(expectedWeight);
    }

    @Test
    void shouldReleaseSalesOrderItems() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> salesOrderItemIds = List.of(1L, 2L, 3L);

        service.releaseSalesOrderItems(salesOrderItemIds);

        verify(service).releaseSalesOrderItems(salesOrderItemIds);
    }

    @Test
    void shouldHandleEmptyListWhenReleasing() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> salesOrderItemIds = List.of();

        service.releaseSalesOrderItems(salesOrderItemIds);

        verify(service).releaseSalesOrderItems(salesOrderItemIds);
    }

    @Test
    void shouldHandleSingleItemRelease() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> salesOrderItemIds = List.of(100L);

        service.releaseSalesOrderItems(salesOrderItemIds);

        verify(service).releaseSalesOrderItems(salesOrderItemIds);
    }

    @Test
    void shouldSummarizeRemainingWeightByPurchaseOrderItemIds() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> purchaseOrderItemIds = List.of(1L, 2L, 3L);
        Map<Long, BigDecimal> expectedWeights = Map.of(
                1L, new BigDecimal("10.5"),
                2L, new BigDecimal("20.75"),
                3L, new BigDecimal("5.0")
        );
        when(service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds))
                .thenReturn(expectedWeights);

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);

        assertThat(result).hasSize(3);
        assertThat(result.get(1L)).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(result.get(2L)).isEqualByComparingTo(new BigDecimal("20.75"));
        assertThat(result.get(3L)).isEqualByComparingTo(new BigDecimal("5.0"));
        verify(service).summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);
    }

    @Test
    void shouldReturnEmptyMapWhenNoRemainingWeight() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> purchaseOrderItemIds = List.of(999L, 1000L);
        when(service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds))
                .thenReturn(Map.of());

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleSingleItemRemainingWeight() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> purchaseOrderItemIds = List.of(1L);
        Map<Long, BigDecimal> expectedWeights = Map.of(1L, new BigDecimal("100.0"));
        when(service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds))
                .thenReturn(expectedWeights);

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);

        assertThat(result).hasSize(1);
        assertThat(result.get(1L)).isEqualByComparingTo(new BigDecimal("100.0"));
    }

    @Test
    void shouldHandleZeroRemainingWeight() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> purchaseOrderItemIds = List.of(1L);
        Map<Long, BigDecimal> expectedWeights = Map.of(1L, BigDecimal.ZERO);
        when(service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds))
                .thenReturn(expectedWeights);

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);

        assertThat(result.get(1L)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldHandleMultipleReleases() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> firstBatch = List.of(1L, 2L);
        Collection<Long> secondBatch = List.of(3L, 4L);

        service.releaseSalesOrderItems(firstBatch);
        service.releaseSalesOrderItems(secondBatch);

        verify(service).releaseSalesOrderItems(firstBatch);
        verify(service).releaseSalesOrderItems(secondBatch);
    }

    @Test
    void shouldHandleLargeBatchSummarize() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Collection<Long> purchaseOrderItemIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        Map<Long, BigDecimal> expectedWeights = Map.of(
                1L, new BigDecimal("1.0"),
                2L, new BigDecimal("2.0"),
                3L, new BigDecimal("3.0"),
                4L, new BigDecimal("4.0"),
                5L, new BigDecimal("5.0"),
                6L, new BigDecimal("6.0"),
                7L, new BigDecimal("7.0"),
                8L, new BigDecimal("8.0"),
                9L, new BigDecimal("9.0"),
                10L, new BigDecimal("10.0")
        );
        when(service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds))
                .thenReturn(expectedWeights);

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);

        assertThat(result).hasSize(10);
        assertThat(result.keySet()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void shouldAllocateWithDifferentLineNumbers() {
        PurchaseItemPieceWeightAppService service = mock(PurchaseItemPieceWeightAppService.class);
        Long sourceId = 1L;
        Integer quantity = 50;
        Long salesItemId = 2L;

        when(service.allocateForSalesOrderItem(sourceId, quantity, salesItemId, 1))
                .thenReturn(new BigDecimal("5.0"));
        when(service.allocateForSalesOrderItem(sourceId, quantity, salesItemId, 2))
                .thenReturn(new BigDecimal("10.0"));
        when(service.allocateForSalesOrderItem(sourceId, quantity, salesItemId, 3))
                .thenReturn(new BigDecimal("15.0"));

        BigDecimal result1 = service.allocateForSalesOrderItem(sourceId, quantity, salesItemId, 1);
        BigDecimal result2 = service.allocateForSalesOrderItem(sourceId, quantity, salesItemId, 2);
        BigDecimal result3 = service.allocateForSalesOrderItem(sourceId, quantity, salesItemId, 3);

        assertThat(result1).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(result2).isEqualByComparingTo(new BigDecimal("10.0"));
        assertThat(result3).isEqualByComparingTo(new BigDecimal("15.0"));
    }
}