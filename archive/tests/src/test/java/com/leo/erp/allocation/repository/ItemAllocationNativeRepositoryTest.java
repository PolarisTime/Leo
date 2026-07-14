package com.leo.erp.allocation.repository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ItemAllocationNativeRepositoryTest {

    @Test
    void shouldDefineSummarizeSalesByPurchaseOrderItemsMethod() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(1L, 2L, 3L);
        Long excludeOrderId = 100L;
        ItemAllocationNativeRepository.AllocationProjection projection = createProjection(1L, 50L, new BigDecimal("10.5"));
        when(repository.summarizeSalesByPurchaseOrderItems(ids, excludeOrderId)).thenReturn(List.of(projection));

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeSalesByPurchaseOrderItems(ids, excludeOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceItemId()).isEqualTo(1L);
        assertThat(result.get(0).getTotalQuantity()).isEqualTo(50L);
        assertThat(result.get(0).getTotalWeightTon()).isEqualByComparingTo(new BigDecimal("10.5"));
        verify(repository).summarizeSalesByPurchaseOrderItems(ids, excludeOrderId);
    }

    @Test
    void shouldHandleNullExcludeOrderIdInSummarizeSales() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(1L);
        when(repository.summarizeSalesByPurchaseOrderItems(ids, null)).thenReturn(List.of());

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeSalesByPurchaseOrderItems(ids, null);

        assertThat(result).isEmpty();
        verify(repository).summarizeSalesByPurchaseOrderItems(ids, null);
    }

    @Test
    void shouldDefineSummarizeInboundByPurchaseOrderItemsMethod() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(1L, 2L);
        Long excludeInboundId = 200L;
        ItemAllocationNativeRepository.AllocationProjection projection = createProjection(1L, 30L, new BigDecimal("5.25"));
        when(repository.summarizeInboundByPurchaseOrderItems(ids, excludeInboundId)).thenReturn(List.of(projection));

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeInboundByPurchaseOrderItems(ids, excludeInboundId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceItemId()).isEqualTo(1L);
        assertThat(result.get(0).getTotalQuantity()).isEqualTo(30L);
        assertThat(result.get(0).getTotalWeightTon()).isEqualByComparingTo(new BigDecimal("5.25"));
        verify(repository).summarizeInboundByPurchaseOrderItems(ids, excludeInboundId);
    }

    @Test
    void shouldHandleNullExcludeInboundIdInSummarizeInbound() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(1L);
        when(repository.summarizeInboundByPurchaseOrderItems(ids, null)).thenReturn(List.of());

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeInboundByPurchaseOrderItems(ids, null);

        assertThat(result).isEmpty();
        verify(repository).summarizeInboundByPurchaseOrderItems(ids, null);
    }

    @Test
    void shouldDefineSummarizeSalesByInboundItemsMethod() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(10L, 20L);
        Long excludeOrderId = 300L;
        ItemAllocationNativeRepository.AllocationProjection projection = createProjection(10L, 25L, new BigDecimal("8.75"));
        when(repository.summarizeSalesByInboundItems(ids, excludeOrderId)).thenReturn(List.of(projection));

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeSalesByInboundItems(ids, excludeOrderId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceItemId()).isEqualTo(10L);
        assertThat(result.get(0).getTotalQuantity()).isEqualTo(25L);
        assertThat(result.get(0).getTotalWeightTon()).isEqualByComparingTo(new BigDecimal("8.75"));
        verify(repository).summarizeSalesByInboundItems(ids, excludeOrderId);
    }

    @Test
    void shouldDefineSummarizeWeightAdjustmentByPurchaseOrderItemsMethod() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(1L, 2L);
        ItemAllocationNativeRepository.AllocationProjection projection = createProjection(1L, null, new BigDecimal("3.5"));
        when(repository.summarizeWeightAdjustmentByPurchaseOrderItems(ids)).thenReturn(List.of(projection));

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeWeightAdjustmentByPurchaseOrderItems(ids);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceItemId()).isEqualTo(1L);
        assertThat(result.get(0).getTotalQuantity()).isNull();
        assertThat(result.get(0).getTotalWeightTon()).isEqualByComparingTo(new BigDecimal("3.5"));
        verify(repository).summarizeWeightAdjustmentByPurchaseOrderItems(ids);
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchingData() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(999L, 1000L);
        when(repository.summarizeSalesByPurchaseOrderItems(ids, null)).thenReturn(List.of());

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeSalesByPurchaseOrderItems(ids, null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnMultipleProjections() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(1L, 2L, 3L);
        List<ItemAllocationNativeRepository.AllocationProjection> projections = List.of(
                createProjection(1L, 10L, new BigDecimal("2.0")),
                createProjection(2L, 20L, new BigDecimal("4.0")),
                createProjection(3L, 30L, new BigDecimal("6.0"))
        );
        when(repository.summarizeSalesByPurchaseOrderItems(ids, null)).thenReturn(projections);

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeSalesByPurchaseOrderItems(ids, null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ItemAllocationNativeRepository.AllocationProjection::getSourceItemId)
                .containsExactly(1L, 2L, 3L);
        assertThat(result).extracting(ItemAllocationNativeRepository.AllocationProjection::getTotalQuantity)
                .containsExactly(10L, 20L, 30L);
    }

    @Test
    void shouldHandleZeroQuantityAndWeight() {
        ItemAllocationNativeRepository repository = mock(ItemAllocationNativeRepository.class);
        Collection<Long> ids = List.of(1L);
        ItemAllocationNativeRepository.AllocationProjection projection = createProjection(1L, 0L, BigDecimal.ZERO);
        when(repository.summarizeSalesByPurchaseOrderItems(ids, null)).thenReturn(List.of(projection));

        List<ItemAllocationNativeRepository.AllocationProjection> result = repository.summarizeSalesByPurchaseOrderItems(ids, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalQuantity()).isEqualTo(0L);
        assertThat(result.get(0).getTotalWeightTon()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private ItemAllocationNativeRepository.AllocationProjection createProjection(Long sourceItemId, Long totalQuantity, BigDecimal totalWeightTon) {
        return new ItemAllocationNativeRepository.AllocationProjection() {
            @Override
            public Long getSourceItemId() {
                return sourceItemId;
            }

            @Override
            public Long getTotalQuantity() {
                return totalQuantity;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return totalWeightTon;
            }
        };
    }
}