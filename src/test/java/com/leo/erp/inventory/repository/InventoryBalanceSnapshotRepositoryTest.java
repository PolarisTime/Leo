package com.leo.erp.inventory.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryBalanceSnapshotRepository 增量 UPSERT 参数装配、重建与校验测试。
 */
@ExtendWith(MockitoExtension.class)
class InventoryBalanceSnapshotRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private JdbcTemplate plainJdbcTemplate;

    @InjectMocks
    private InventoryBalanceSnapshotRepository repository;

    @Test
    void applyDelta_shouldUseSentinelZeroWhenWarehouseMissing() {
        repository.applyDelta(100L, null, "M001", null, null, 5, new BigDecimal("20000.00"));

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("materialId")).isEqualTo(100L);
        assertThat(params.getValue("warehouseId"))
                .isEqualTo(InventoryBalanceSnapshotRepository.NO_WAREHOUSE);
        assertThat(params.getValue("quantityDelta")).isEqualTo(5);
        assertThat(params.getValue("amountDelta")).isEqualTo(new BigDecimal("20000.00"));
    }

    @Test
    void applyDelta_shouldBindRealWarehouseAndNegativeDelta() {
        repository.applyDelta(100L, 9L, "M001", "库房A", "B1", -5, new BigDecimal("-20000.00"));

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("warehouseId")).isEqualTo(9L);
        assertThat(params.getValue("quantityDelta")).isEqualTo(-5);
        assertThat(params.getValue("warehouseName")).isEqualTo("库房A");
        assertThat(params.getValue("batchNo")).isEqualTo("B1");
    }

    @Test
    void applyDelta_shouldZeroAmountWhenResultingQuantityIsZero() {
        repository.applyDelta(100L, 9L, "M001", "库房A", "B1", -5, new BigDecimal("-40.01"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        assertThat(sqlCaptor.getValue())
                .contains("WHEN inv_balance.quantity + EXCLUDED.quantity = 0 THEN 0");
    }

    @Test
    void rebuild_shouldUpsertThenDeleteStaleRows() {
        when(jdbcTemplate.getJdbcTemplate()).thenReturn(plainJdbcTemplate);
        when(plainJdbcTemplate.update(anyString())).thenReturn(4).thenReturn(1);

        int affected = repository.rebuild();

        assertThat(affected).isEqualTo(4);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(plainJdbcTemplate, org.mockito.Mockito.times(2)).update(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().get(0)).contains("INSERT INTO inv_balance");
        assertThat(sqlCaptor.getAllValues().get(0)).contains("GROUP BY t.material_id");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("DELETE FROM inv_balance");
    }

    @Test
    void findMismatches_shouldQueryReconciliationSql() {
        InventoryBalanceSnapshotRepository.BalanceMismatch mismatch =
                new InventoryBalanceSnapshotRepository.BalanceMismatch(
                        100L, 0L, 5L, 4L, new BigDecimal("20000.00"), new BigDecimal("16000.00"));
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(mismatch));

        List<InventoryBalanceSnapshotRepository.BalanceMismatch> result = repository.findMismatches();

        assertThat(result).containsExactly(mismatch);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("FULL OUTER JOIN inv_balance b");
    }

    @Test
    void findMismatches_shouldTolerateTinyResidualOnlyWhenQuantityZero() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.findMismatches();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue())
                .contains("ABS(COALESCE(l.amount, 0) - COALESCE(b.amount, 0)) > :zeroQtyAmountTolerance")
                .contains("COALESCE(l.quantity, 0) = 0 AND COALESCE(b.quantity, 0) = 0");
        assertThat(paramsCaptor.getValue().getValue("zeroQtyAmountTolerance"))
                .isEqualTo(new BigDecimal("0.05"));
    }
}
