package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderItemQueryServiceTest {

    @Test
    void findActiveByIdInShouldReturnEmptyForNullOrEmptyIds() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        SalesOrderItemQueryService service = new SalesOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.findActiveByIdIn(null)).isEmpty();
        assertThat(service.findActiveByIdIn(List.of())).isEmpty();
    }

    @Test
    void requireActiveByIdShouldThrowWhenNotFound() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        when(repository.findActiveByIdIn(List.of(99L))).thenReturn(List.of());

        SalesOrderItemQueryService service = new SalesOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThatThrownBy(() -> service.requireActiveById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requireActiveByIdShouldReturnItem() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(1L);
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        SalesOrderItemQueryService service = new SalesOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.requireActiveById(1L)).isEqualTo(item);
    }

    @Test
    void summarizeAllocatedQuantityBySourceInboundItemIdsShouldReturnEmptyForNullOrEmptyIds() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        SalesOrderItemQueryService service = new SalesOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.summarizeAllocatedQuantityBySourceInboundItemIds(null, null)).isEmpty();
        assertThat(service.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(), null)).isEmpty();
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsShouldReturnEmptyForNullOrEmptyIds() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        SalesOrderItemQueryService service = new SalesOrderItemQueryService(
                repository, mock(ResourceRecordAccessGuard.class)
        );

        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(null, null)).isEmpty();
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(), null)).isEmpty();
    }

    @Test
    void summarizeAllocatedQuantityBySourceInboundItemIdsShouldReturnSummaryMap() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        SalesOrderItemQueryService service = new SalesOrderItemQueryService(repository, accessGuard);

        SalesOrderItemRepository.SourceInboundAllocationSummary summary1 =
                mock(SalesOrderItemRepository.SourceInboundAllocationSummary.class);
        when(summary1.getSourceInboundItemId()).thenReturn(1L);
        when(summary1.getTotalQuantity()).thenReturn(15L);

        SalesOrderItemRepository.SourceInboundAllocationSummary summary2 =
                mock(SalesOrderItemRepository.SourceInboundAllocationSummary.class);
        when(summary2.getSourceInboundItemId()).thenReturn(2L);
        when(summary2.getTotalQuantity()).thenReturn(8L);

        when(repository.summarizeAllocatedQuantityBySourceInboundItemIds(eq(List.of(1L, 2L)), eq(null)))
                .thenReturn(List.of(summary1, summary2));

        Map<Long, Long> result = service.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(1L, 2L), null);

        assertThat(result).hasSize(2);
        assertThat(result.get(1L)).isEqualTo(15L);
        assertThat(result.get(2L)).isEqualTo(8L);
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsShouldReturnSummaryMap() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        SalesOrderItemQueryService service = new SalesOrderItemQueryService(repository, accessGuard);

        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary summary1 =
                mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(summary1.getSourcePurchaseOrderItemId()).thenReturn(10L);
        when(summary1.getTotalQuantity()).thenReturn(20L);

        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(eq(List.of(10L)), eq(1L)))
                .thenReturn(List.of(summary1));

        Map<Long, Long> result = service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(10L), 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(10L)).isEqualTo(20L);
    }

    @Test
    void findActiveByIdInShouldCallAccessGuardForEachItem() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        SalesOrderItemQueryService service = new SalesOrderItemQueryService(repository, accessGuard);

        SalesOrder order = new SalesOrder();
        order.setId(1L);
        SalesOrderItem item1 = new SalesOrderItem();
        item1.setId(10L);
        item1.setSalesOrder(order);
        SalesOrderItem item2 = new SalesOrderItem();
        item2.setId(20L);
        item2.setSalesOrder(order);

        when(repository.findActiveByIdIn(List.of(10L, 20L))).thenReturn(List.of(item1, item2));

        List<SalesOrderItem> result = service.findActiveByIdIn(List.of(10L, 20L));

        assertThat(result).hasSize(2);
        verify(accessGuard, atLeastOnce()).assertCurrentUserCanAccess("sales-order", "read", order);
    }

    @Test
    void findActiveByIdInShouldSkipAccessGuardWhenSalesOrderNull() {
        SalesOrderItemRepository repository = mock(SalesOrderItemRepository.class);
        ResourceRecordAccessGuard accessGuard = mock(ResourceRecordAccessGuard.class);
        SalesOrderItemQueryService service = new SalesOrderItemQueryService(repository, accessGuard);

        SalesOrderItem item = new SalesOrderItem();
        item.setId(10L);
        item.setSalesOrder(null);

        when(repository.findActiveByIdIn(List.of(10L))).thenReturn(List.of(item));

        List<SalesOrderItem> result = service.findActiveByIdIn(List.of(10L));

        assertThat(result).hasSize(1);
        verify(accessGuard, org.mockito.Mockito.never()).assertCurrentUserCanAccess(any(), any(), any());
    }
}
