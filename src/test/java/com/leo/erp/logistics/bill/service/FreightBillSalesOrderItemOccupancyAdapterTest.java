package com.leo.erp.logistics.bill.service;

import com.leo.erp.logistics.bill.repository.FreightBillItemRepository;
import com.leo.erp.logistics.bill.repository.FreightBillItemRepository.FreightBillItemOccupancySummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物流行级占用适配器测试：数量真源为明细聚合，跨单累计且不排除当前单（适配器场景无当前单）。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillSalesOrderItemOccupancyAdapterTest {

    @Mock
    private FreightBillItemRepository itemRepository;

    @InjectMocks
    private FreightBillSalesOrderItemOccupancyAdapter adapter;

    @Test
    void occupiedQuantities_shouldAggregateAcrossBillsAndKeepAllSourceItems() {
        when(itemRepository.summarizeOccupiedQuantities(any(), isNull()))
                .thenReturn(List.of(summary(11L, 8), summary(12L, 3)));

        Map<Long, Integer> occupied = adapter.occupiedQuantities(List.of(11L, 12L));

        assertThat(occupied).containsEntry(11L, 8).containsEntry(12L, 3);
        verify(itemRepository).summarizeOccupiedQuantities(any(), isNull());
    }

    @Test
    void occupiedQuantities_shouldReturnEmptyForNullInputWithoutQuerying() {
        assertThat(adapter.occupiedQuantities(null)).isEmpty();
        verify(itemRepository, never()).summarizeOccupiedQuantities(any(), anyLong());
    }

    @Test
    void occupiedQuantities_shouldReturnEmptyForEmptyInputWithoutQuerying() {
        assertThat(adapter.occupiedQuantities(List.of())).isEmpty();
        verify(itemRepository, never()).summarizeOccupiedQuantities(any(), any());
    }

    private FreightBillItemOccupancySummary summary(Long sourceItemId, int quantity) {
        return new FreightBillItemOccupancySummary() {
            @Override
            public Long getSourceSalesOrderItemId() {
                return sourceItemId;
            }

            @Override
            public Long getTotalQuantity() {
                return (long) quantity;
            }
        };
    }
}
