package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderItemQueryService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderItemQueryServiceTest {

    @Mock
    private SalesOrderItemRepository repository;

    @InjectMocks
    private SalesOrderItemQueryService service;

    @Test
    void findActiveByIdIn_shouldReturnEmptyForNull() {
        assertThat(service.findActiveByIdIn(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findActiveByIdIn_shouldReturnEmptyForEmptyCollection() {
        assertThat(service.findActiveByIdIn(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findActiveByIdIn_shouldDelegateToRepository() {
        SalesOrderItem item = new SalesOrderItem();
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        assertThat(service.findActiveByIdIn(List.of(1L))).containsExactly(item);
    }

    @Test
    void requireActiveById_shouldReturnItemWhenPresent() {
        SalesOrderItem item = new SalesOrderItem();
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of(item));

        assertThat(service.requireActiveById(1L)).isSameAs(item);
    }

    @Test
    void requireActiveById_shouldThrowNotFoundWhenMissing() {
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireActiveById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void summarizeByInbound_shouldReturnEmptyForNull() {
        assertThat(service.summarizeAllocatedQuantityBySourceInboundItemIds(null, 5L)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeByInbound_shouldMapRepositorySummary() {
        SalesOrderItemRepository.SourceInboundAllocationSummary summary =
                mockInboundSummary(11L, 30L);
        when(repository.summarizeAllocatedQuantityBySourceInboundItemIds(any(), any()))
                .thenReturn(List.of(summary));

        Map<Long, Long> result =
                service.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(11L), 5L);

        assertThat(result).containsEntry(11L, 30L);
    }

    @Test
    void summarizeByPurchaseOrder_shouldReturnEmptyForEmptyInput() {
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(), 5L)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeByPurchaseOrder_shouldMapRepositorySummary() {
        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary summary =
                mockPurchaseSummary(21L, 40L);
        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(any(), any()))
                .thenReturn(List.of(summary));

        Map<Long, Long> result =
                service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(21L), 5L);

        assertThat(result).containsEntry(21L, 40L);
    }

    private SalesOrderItemRepository.SourceInboundAllocationSummary mockInboundSummary(Long id, Long qty) {
        SalesOrderItemRepository.SourceInboundAllocationSummary s =
                org.mockito.Mockito.mock(SalesOrderItemRepository.SourceInboundAllocationSummary.class);
        when(s.getSourceInboundItemId()).thenReturn(id);
        when(s.getTotalQuantity()).thenReturn(qty);
        return s;
    }

    private SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary mockPurchaseSummary(Long id, Long qty) {
        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary s =
                org.mockito.Mockito.mock(SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary.class);
        when(s.getSourcePurchaseOrderItemId()).thenReturn(id);
        when(s.getTotalQuantity()).thenReturn(qty);
        return s;
    }

    @Test
    void summarizeByInbound_shouldReturnEmptyForEmptyInput() {
        assertThat(service.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(), 5L)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeByPurchaseOrder_shouldReturnEmptyForNull() {
        assertThat(service.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(null, 5L)).isEmpty();
        verifyNoInteractions(repository);
    }
}
