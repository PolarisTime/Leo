package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.api.ItemAllocationQuery;
import com.leo.erp.allocation.api.ItemAllocationSummary;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderAvailabilityServiceTest {

    @Mock
    private PurchaseInboundItemQueryService purchaseInboundItemQueryService;

    @Mock
    private ItemAllocationQuery itemAllocationQuery;

    private PurchaseOrderAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderAvailabilityService(purchaseInboundItemQueryService, itemAllocationQuery);
    }

    private PurchaseOrderItem item(Long id, Integer quantity) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setQuantity(quantity);
        return item;
    }

    private PurchaseOrder order(Long id, List<PurchaseOrderItem> items) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(id);
        order.setItems(items);
        return order;
    }

    @Test
    void loadInboundAllocatedQuantityMap_shouldReturnEmptyWhenOrderHasNoItems() {
        Map<Long, Integer> result = service.loadInboundAllocatedQuantityMap(order(100L, List.of()));

        assertThat(result).isEmpty();
        verify(purchaseInboundItemQueryService, never())
                .summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loadInboundAllocatedQuantityMap_shouldConvertLongToInteger() {
        PurchaseOrder order = order(100L, List.of(item(1L, 10), item(2L, 5)));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 3L, 2L, 0L));

        Map<Long, Integer> result = service.loadInboundAllocatedQuantityMap(order);

        assertThat(result).containsEntry(1L, 3).containsEntry(2L, 0);
    }

    @Test
    void loadSalesAllocatedQuantityMap_shouldMapSummaries() {
        PurchaseOrder order = order(100L, List.of(item(1L, 10), item(2L, 5)));
        when(itemAllocationQuery.summarizeSalesByPurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of(new ItemAllocationSummary(1L, 4L), new ItemAllocationSummary(2L, 5L)));

        Map<Long, Integer> result = service.loadSalesAllocatedQuantityMap(order);

        assertThat(result).containsEntry(1L, 4).containsEntry(2L, 5);
    }

    @Test
    void loadSalesAllocatedQuantityMap_shouldReturnEmptyWhenOrderHasNoItems() {
        Map<Long, Integer> result = service.loadSalesAllocatedQuantityMap(order(100L, List.of()));

        assertThat(result).isEmpty();
        verify(itemAllocationQuery, never())
                .summarizeSalesByPurchaseOrderItemIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void remainingQuantity_shouldSubtractAllocation() {
        PurchaseOrderItem item = item(1L, 10);

        assertThat(service.remainingQuantity(item, Map.of())).isEqualTo(10);
        assertThat(service.remainingQuantity(item, Map.of(1L, 4))).isEqualTo(6);
    }

    @Test
    void remainingQuantity_shouldNeverGoNegative() {
        PurchaseOrderItem item = item(1L, 10);

        assertThat(service.remainingQuantity(item, Map.of(1L, 20))).isZero();
    }

    @Test
    void salesRemainingWeightTon_shouldUseFullWeightWhenNothingAllocated() {
        PurchaseOrderItem item = item(1L, 10);
        item.setWeightTon(new BigDecimal("2.5"));
        item.setPieceWeightTon(new BigDecimal("0.25"));

        BigDecimal result = service.salesRemainingWeightTon(item, Map.of());

        assertThat(result).isEqualByComparingTo("2.5");
    }

    @Test
    void salesRemainingWeightTon_shouldComputeFromRemainingPieces() {
        PurchaseOrderItem item = item(1L, 10);
        item.setWeightTon(new BigDecimal("2.5"));
        item.setPieceWeightTon(new BigDecimal("0.25"));

        BigDecimal result = service.salesRemainingWeightTon(item, Map.of(1L, 4));

        assertThat(result).isEqualByComparingTo("1.5");
    }

    @Test
    void buildInboundImportableQuantityMap_shouldReturnEmptyForNullOrEmptyOrders() {
        assertThat(service.buildInboundImportableQuantityMap(null, null)).isEmpty();
        assertThat(service.buildInboundImportableQuantityMap(List.of(), null)).isEmpty();
    }

    @Test
    void buildInboundImportableQuantityMap_shouldReturnZeroWhenNoItemIds() {
        PurchaseOrder order = order(100L, List.of(item(null, 5)));

        Map<Long, Integer> result = service.buildInboundImportableQuantityMap(List.of(order), null);

        assertThat(result).containsEntry(100L, 0).hasSize(1);
    }

    @Test
    void buildInboundImportableQuantityMap_shouldZeroOrderWithExistingInboundAllocation() {
        PurchaseOrder allocatedOrder = order(100L, List.of(item(1L, 10), item(2L, 5)));
        PurchaseOrder freeOrder = order(200L, List.of(item(3L, 7)));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(1L, 2L, 3L)))
                .thenReturn(Map.of(1L, 2L));

        Map<Long, Integer> result = service.buildInboundImportableQuantityMap(
                List.of(allocatedOrder, freeOrder), null);

        assertThat(result).containsEntry(100L, 0).containsEntry(200L, 7);
    }

    @Test
    void buildInboundImportableQuantityMap_shouldExcludeCurrentInboundRecord() {
        PurchaseOrder order = order(100L, List.of(item(1L, 10), item(2L, 5)));
        when(purchaseInboundItemQueryService
                .summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(List.of(1L, 2L), 55L))
                .thenReturn(Map.of(1L, 2L));

        Map<Long, Integer> result = service.buildInboundImportableQuantityMap(List.of(order), 55L);

        assertThat(result).containsEntry(100L, 0);
        verify(purchaseInboundItemQueryService, never())
                .summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void buildInboundImportableQuantityMap_shouldFilterNullItemIds() {
        PurchaseOrder order = order(100L, List.of(item(null, 5), item(1L, 3)));
        when(purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(1L)))
                .thenReturn(Map.of());

        Map<Long, Integer> result = service.buildInboundImportableQuantityMap(List.of(order), null);

        assertThat(result).containsEntry(100L, 8);
    }
}
