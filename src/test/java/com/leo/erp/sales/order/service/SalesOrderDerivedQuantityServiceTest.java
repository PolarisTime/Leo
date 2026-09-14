package com.leo.erp.sales.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SalesOrderDerivedQuantityService 聚合测试：净交付 = 已出库 - 已退货。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderDerivedQuantityServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private SalesOrderDerivedQuantityService service;

    private void stubAggregates(String keyColumn, long key, int delivered, int returned) {
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowCallbackHandler handler = invocation.getArgument(2);
            boolean returnedQuery = sql.contains("so_sales_return_item item")
                    || sql.contains("so_sales_return_item return_item");
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong(keyColumn)).thenReturn(key);
            when(resultSet.getInt("total_quantity")).thenReturn(returnedQuery ? returned : delivered);
            handler.processRow(resultSet);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
    }

    @Test
    void itemQuantities_shouldComputeDeliveredMinusReturned() {
        stubAggregates("item_id", 1L, 10, 4);

        Map<Long, SalesOrderDerivedQuantityService.Quantities> quantities =
                service.itemQuantities(List.of(1L));

        assertThat(quantities.get(1L).deliveredQuantity()).isEqualTo(10);
        assertThat(quantities.get(1L).returnedQuantity()).isEqualTo(4);
        assertThat(quantities.get(1L).deliveredNetQuantity()).isEqualTo(6);
    }

    @Test
    void orderQuantities_shouldComputeDeliveredMinusReturned() {
        stubAggregates("order_id", 7L, 25, 5);

        Map<Long, SalesOrderDerivedQuantityService.Quantities> quantities =
                service.orderQuantities(List.of(7L));

        assertThat(quantities.get(7L).deliveredQuantity()).isEqualTo(25);
        assertThat(quantities.get(7L).returnedQuantity()).isEqualTo(5);
        assertThat(quantities.get(7L).deliveredNetQuantity()).isEqualTo(20);
    }

    @Test
    void itemQuantities_shouldReturnEmptyWhenNoIds() {
        assertThat(service.itemQuantities(List.of())).isEmpty();
    }

    @Test
    void reservedOutboundQuantities_shouldSumNonDeletedOutbounds() {
        stubAggregates("item_id", 1L, 7, 0);

        Map<Long, Integer> reserved = service.reservedOutboundQuantities(List.of(1L));

        assertThat(reserved).containsEntry(1L, 7);
    }

    @Test
    void reservedOutboundQuantities_shouldReturnEmptyWhenNoIds() {
        assertThat(service.reservedOutboundQuantities(List.of())).isEmpty();
    }
}
