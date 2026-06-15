package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseInboundItemQueryServiceTest {

    @Test
    void findAllActiveByIdInShouldReturnEmptyForNullOrEmptyIds() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.findAllActiveByIdIn(null)).isEmpty();
        assertThat(service.findAllActiveByIdIn(List.of())).isEmpty();
    }

    @Test
    void requireActiveByIdShouldThrowWhenNotFound() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        when(repository.findAllActiveByIdIn(List.of(99L))).thenReturn(List.of());

        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> service.requireActiveById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requireActiveByIdShouldReturnItem() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(1L);
        when(repository.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.requireActiveById(1L)).isEqualTo(item);
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIdsShouldReturnEmptyForNullOrEmptyIds() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.findAllActiveBySourcePurchaseOrderItemIds(null)).isEmpty();
        assertThat(service.findAllActiveBySourcePurchaseOrderItemIds(List.of())).isEmpty();
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsShouldReturnEmptyForNullOrEmptyIds() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(null)).isEmpty();
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of())).isEmpty();
    }

    @Test
    void summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsShouldReturnEmptyForNullOrEmptyIds() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(null)).isEmpty();
        assertThat(service.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of())).isEmpty();
    }

    @Test
    void findAllActiveByIdInShouldReturnItemsAndCheckAccess() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);

        com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound inbound = new com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound();
        inbound.setId(100L);

        PurchaseInboundItem item1 = new PurchaseInboundItem();
        item1.setId(1L);
        item1.setPurchaseInbound(inbound);

        PurchaseInboundItem item2 = new PurchaseInboundItem();
        item2.setId(2L);
        item2.setPurchaseInbound(inbound);

        when(repository.findAllActiveByIdIn(List.of(1L, 2L))).thenReturn(List.of(item1, item2));

        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(repository, accessGuard);

        List<PurchaseInboundItem> result = service.findAllActiveByIdIn(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        verify(accessGuard).assertCurrentUserCanAccess("purchase-inbound", "read", inbound);
    }

    @Test
    void findAllActiveByIdInShouldSkipAccessCheckWhenParentIsNull() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(1L);
        item.setPurchaseInbound(null);

        when(repository.findAllActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(repository, accessGuard);

        List<PurchaseInboundItem> result = service.findAllActiveByIdIn(List.of(1L));

        assertThat(result).singleElement().isEqualTo(item);
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIdsShouldReturnItems() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);

        com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound inbound = new com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound();
        inbound.setId(100L);

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(1L);
        item.setPurchaseInbound(inbound);
        item.setSourcePurchaseOrderItemId(201L);

        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(item));

        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(repository, accessGuard);

        List<PurchaseInboundItem> result = service.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L));

        assertThat(result).singleElement().isEqualTo(item);
        verify(accessGuard).assertCurrentUserCanAccess("purchase-inbound", "read", inbound);
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsShouldReturnResults() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        PurchaseInboundItemRepository.PurchaseOrderAllocationSummary summary =
                mock(PurchaseInboundItemRepository.PurchaseOrderAllocationSummary.class);
        when(summary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(summary.getTotalQuantity()).thenReturn(8L);

        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(201L)))
                .thenReturn(List.of(summary));

        Map<Long, Long> result = service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(201L));

        assertThat(result).containsEntry(201L, 8L);
    }

    @Test
    void summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsShouldReturnResults() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        PurchaseInboundItemRepository.PurchaseOrderWeightAdjustmentSummary summary =
                mock(PurchaseInboundItemRepository.PurchaseOrderWeightAdjustmentSummary.class);
        when(summary.getSourcePurchaseOrderItemId()).thenReturn(201L);
        when(summary.getTotalWeightAdjustmentTon()).thenReturn(new BigDecimal("0.050"));

        when(repository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(List.of(201L), null))
                .thenReturn(List.of(summary));

        Map<Long, BigDecimal> result = service.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(List.of(201L));

        assertThat(result).containsEntry(201L, new BigDecimal("0.050"));
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIdsShouldReturnEmptyForNullOrEmptyIdsAgain() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.findAllActiveBySourcePurchaseOrderItemIds(null)).isEmpty();
        assertThat(service.findAllActiveBySourcePurchaseOrderItemIds(List.of())).isEmpty();
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIdsShouldSkipAccessCheckWhenParentIsNull() {
        PurchaseInboundItemRepository repository = mock(PurchaseInboundItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(1L);
        item.setPurchaseInbound(null);

        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L))).thenReturn(List.of(item));

        PurchaseInboundItemQueryService service = new PurchaseInboundItemQueryService(repository, accessGuard);

        List<PurchaseInboundItem> result = service.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L));

        assertThat(result).singleElement().isEqualTo(item);
    }
}
