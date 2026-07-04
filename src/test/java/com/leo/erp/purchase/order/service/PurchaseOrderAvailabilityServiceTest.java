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

    private PurchaseOrderAvailabilityService service(PurchaseInboundItemQueryService inboundQueryService,
                                                     ItemAllocationNativeRepository allocationRepository) {
        return new PurchaseOrderAvailabilityService(
                inboundQueryService,
                allocationRepository,
                mock(PurchaseOrderItemPieceWeightService.class)
        );
    }
}
