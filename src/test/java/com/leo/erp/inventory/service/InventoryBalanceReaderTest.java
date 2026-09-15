package com.leo.erp.inventory.service;

import com.leo.erp.inventory.repository.InventoryBalanceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryBalanceReader 快照读取测试：命中返回首行、无快照行返回 EMPTY、
 * 仓库为空时落哨兵 0、行映射数量与金额。
 */
@ExtendWith(MockitoExtension.class)
class InventoryBalanceReaderTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private InventoryBalanceReader reader;

    @Test
    void currentBalance_shouldReturnFirstRowAndBindDimensions() {
        InventoryBalanceTotals row = new InventoryBalanceTotals(12L, new BigDecimal("3600.00"));
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        InventoryBalanceTotals result = reader.currentBalance(100L, 3L);

        assertThat(result).isEqualTo(row);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("materialId")).isEqualTo(100L);
        assertThat(captor.getValue().getValue("warehouseId")).isEqualTo(3L);
    }

    @Test
    void currentBalance_shouldReturnEmptyWhenNoSnapshotRow() {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        InventoryBalanceTotals result = reader.currentBalance(100L, 3L);

        assertThat(result).isSameAs(InventoryBalanceTotals.EMPTY);
    }

    @Test
    void currentBalance_shouldUseNoWarehouseSentinelWhenWarehouseAbsent() {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        reader.currentBalance(100L, null);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("warehouseId"))
                .isEqualTo(InventoryBalanceSnapshotRepository.NO_WAREHOUSE);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void currentBalance_shouldMapQuantityAndAmountFromRow() throws Exception {
        ArgumentCaptor<RowMapper<InventoryBalanceTotals>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        reader.currentBalance(1L, 2L);

        verify(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), captor.capture());
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("quantity")).thenReturn(7L);
        when(rs.getBigDecimal("amount")).thenReturn(new BigDecimal("12.34"));

        InventoryBalanceTotals totals = captor.getValue().mapRow(rs, 0);

        assertThat(totals.quantity()).isEqualTo(7L);
        assertThat(totals.amount()).isEqualByComparingTo("12.34");
    }
}
