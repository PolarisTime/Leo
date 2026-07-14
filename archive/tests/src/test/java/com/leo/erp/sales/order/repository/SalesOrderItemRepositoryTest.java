package com.leo.erp.sales.order.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderItemRepositoryTest {

    @Mock
    private SalesOrderItemRepository repository;

    @Test
    void findActiveByIdIn_shouldReturnItemsForActiveOrders() {
        SalesOrderItem item1 = new SalesOrderItem();
        item1.setId(1L);
        item1.setMaterialCode("M001");
        item1.setQuantity(10);
        item1.setAmount(new BigDecimal("1000.00"));

        SalesOrderItem item2 = new SalesOrderItem();
        item2.setId(2L);
        item2.setMaterialCode("M002");
        item2.setQuantity(20);
        item2.setAmount(new BigDecimal("2000.00"));

        when(repository.findActiveByIdIn(List.of(1L, 2L))).thenReturn(List.of(item1, item2));

        List<SalesOrderItem> result = repository.findActiveByIdIn(List.of(1L, 2L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findActiveByIdIn_shouldNotReturnItemsForDeletedOrders() {
        when(repository.findActiveByIdIn(List.of(1L))).thenReturn(List.of());

        List<SalesOrderItem> result = repository.findActiveByIdIn(List.of(1L));

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveByIdIn_shouldReturnEmptyWhenNoMatch() {
        when(repository.findActiveByIdIn(List.of(999L, 1000L))).thenReturn(List.of());

        List<SalesOrderItem> result = repository.findActiveByIdIn(List.of(999L, 1000L));

        assertThat(result).isEmpty();
    }

    @Test
    void summarizeAllocatedQuantityBySourceInboundItemIds_shouldReturnSummary() {
        SalesOrderItemRepository.SourceInboundAllocationSummary summary = new SalesOrderItemRepository.SourceInboundAllocationSummary() {
            @Override
            public Long getSourceInboundItemId() { return 1L; }
            @Override
            public Long getTotalQuantity() { return 30L; }
            @Override
            public BigDecimal getTotalWeightTon() { return new BigDecimal("4.0"); }
        };

        when(repository.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(1L), null))
                .thenReturn(List.of(summary));

        List<SalesOrderItemRepository.SourceInboundAllocationSummary> result =
                repository.summarizeAllocatedQuantityBySourceInboundItemIds(List.of(1L), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourceInboundItemId()).isEqualTo(1L);
        assertThat(result.get(0).getTotalQuantity()).isEqualTo(30L);
        assertThat(result.get(0).getTotalWeightTon()).isEqualByComparingTo("4.0");
    }

    @Test
    void summarizeAllocatedQuantityBySourcePurchaseOrderItemIds_shouldReturnSummary() {
        SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary summary = new SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary() {
            @Override
            public Long getSourcePurchaseOrderItemId() { return 1L; }
            @Override
            public Long getTotalQuantity() { return 30L; }
            @Override
            public BigDecimal getTotalWeightTon() { return new BigDecimal("4.0"); }
        };

        when(repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(1L), null))
                .thenReturn(List.of(summary));

        List<SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary> result =
                repository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(List.of(1L), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSourcePurchaseOrderItemId()).isEqualTo(1L);
        assertThat(result.get(0).getTotalQuantity()).isEqualTo(30L);
        assertThat(result.get(0).getTotalWeightTon()).isEqualByComparingTo("4.0");
    }
}
