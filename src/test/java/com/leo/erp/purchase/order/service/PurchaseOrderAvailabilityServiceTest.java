package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderAvailabilityServiceTest {

    @Test
    void shouldReturnEmptyImportableQuantityMapWhenOrdersAreMissing() {
        PurchaseOrderAvailabilityService service = service(
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class)
        );

        assertThat(service.buildImportableQuantityMap(
                null,
                PurchaseOrderAvailabilityService.ImportCandidateUsage.PURCHASE_INBOUND
        )).isEmpty();
        assertThat(service.buildImportableQuantityMap(
                List.of(),
                PurchaseOrderAvailabilityService.ImportCandidateUsage.PURCHASE_INBOUND
        )).isEmpty();
    }

    @Test
    void shouldReturnZeroImportableQuantityWhenOrdersHaveNoPersistedItemIds() {
        PurchaseOrderAvailabilityService service = service(
                mock(PurchaseInboundItemQueryService.class),
                mock(ItemAllocationNativeRepository.class)
        );
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(null);
        item.setQuantity(10);
        order.getItems().add(item);

        Map<Long, Integer> result = service.buildImportableQuantityMap(
                List.of(order),
                PurchaseOrderAvailabilityService.ImportCandidateUsage.PURCHASE_INBOUND
        );

        assertThat(result).containsEntry(1L, 0);
    }

    @Test
    void shouldTreatNullInboundAllocationSummaryAsUnallocated() {
        PurchaseInboundItemQueryService inboundQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderAvailabilityService service = service(
                inboundQueryService,
                mock(ItemAllocationNativeRepository.class)
        );
        PurchaseOrder order = new PurchaseOrder();
        order.setId(2L);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(21L);
        item.setQuantity(7);
        order.getItems().add(item);
        when(inboundQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(21L)))
                .thenReturn(null);

        Map<Long, Integer> result = service.buildImportableQuantityMap(
                List.of(order),
                PurchaseOrderAvailabilityService.ImportCandidateUsage.PURCHASE_INBOUND
        );

        assertThat(result).containsEntry(2L, 7);
    }

    @Test
    void shouldRejectNullImportCandidateUsage() {
        assertThatThrownBy(() -> PurchaseOrderAvailabilityService.ImportCandidateUsage.from(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("usage 不支持当前导入场景");
    }

    @Test
    void shouldExcludeCurrentInboundWhenCalculatingImportableQuantity() {
        PurchaseInboundItemQueryService inboundQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseOrderAvailabilityService service = service(
                inboundQueryService,
                mock(ItemAllocationNativeRepository.class)
        );
        PurchaseOrder order = order(3L, 31L, 10);
        when(inboundQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                List.of(31L),
                9001L
        )).thenReturn(Map.of(31L, 4L));

        Map<Long, Integer> result = service.buildImportableQuantityMap(
                List.of(order),
                PurchaseOrderAvailabilityService.ImportCandidateUsage.PURCHASE_INBOUND,
                9001L
        );

        assertThat(result).containsEntry(3L, 6);
    }

    @Test
    void shouldExcludeCurrentSalesOrderWhenCalculatingImportableQuantity() {
        ItemAllocationNativeRepository allocationRepository = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderAvailabilityService service = service(
                mock(PurchaseInboundItemQueryService.class),
                allocationRepository
        );
        PurchaseOrder order = order(4L, 41L, 8);
        ItemAllocationNativeRepository.AllocationProjection projection =
                mock(ItemAllocationNativeRepository.AllocationProjection.class);
        when(projection.getSourceItemId()).thenReturn(41L);
        when(projection.getTotalQuantity()).thenReturn(3L);
        when(allocationRepository.summarizeSalesByPurchaseOrderItems(List.of(41L), 9002L))
                .thenReturn(List.of(projection));

        Map<Long, Integer> result = service.buildImportableQuantityMap(
                List.of(order),
                PurchaseOrderAvailabilityService.ImportCandidateUsage.SALES_ORDER,
                9002L
        );

        assertThat(result).containsEntry(4L, 5);
    }

    @Test
    void shouldUseOriginalQuantityForPurchaseContractCandidates() {
        PurchaseInboundItemQueryService inboundQueryService = mock(PurchaseInboundItemQueryService.class);
        ItemAllocationNativeRepository allocationRepository = mock(ItemAllocationNativeRepository.class);
        PurchaseOrderAvailabilityService service = service(inboundQueryService, allocationRepository);
        PurchaseOrder order = order(5L, 51L, 12);

        Map<Long, Integer> result = service.buildImportableQuantityMap(
                List.of(order),
                PurchaseOrderAvailabilityService.ImportCandidateUsage.from("purchase-contract"),
                null
        );

        assertThat(result).containsEntry(5L, 12);
        verify(inboundQueryService, never())
                .summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(org.mockito.ArgumentMatchers.any());
        verify(allocationRepository, never())
                .summarizeSalesByPurchaseOrderItems(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private PurchaseOrder order(Long orderId, Long itemId, int quantity) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(orderId);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(itemId);
        item.setQuantity(quantity);
        order.getItems().add(item);
        return order;
    }

    private PurchaseOrderAvailabilityService service(PurchaseInboundItemQueryService inboundQueryService,
                                                     ItemAllocationNativeRepository allocationRepository) {
        return new PurchaseOrderAvailabilityService(
                inboundQueryService,
                allocationRepository,
                mock(PurchaseOrderItemPieceWeightService.class)
        );
    }
}
