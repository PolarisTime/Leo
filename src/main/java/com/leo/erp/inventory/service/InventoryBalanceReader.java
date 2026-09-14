package com.leo.erp.inventory.service;

import com.leo.erp.inventory.repository.InventoryBalanceSnapshotRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存余额读取器：直接读取 inv_balance 增量快照的 (material_id, warehouse_id) 维度余额。
 *
 * <p>不再对 inv_transaction 做全历史 SUM 聚合；快照由记账/软删在同一事务内维护，
 * 无快照行表示该维度余额为零。
 */
@Component
public class InventoryBalanceReader {

    private static final String BALANCE_SQL = """
            SELECT quantity, amount
            FROM inv_balance
            WHERE material_id = :materialId
              AND warehouse_id = :warehouseId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryBalanceReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 读取当前库存余额；无快照行时返回 {@link InventoryBalanceTotals#EMPTY}。
     *
     * @param warehouseId 仓库ID，null 表示无仓库维度（落哨兵 0）
     */
    public InventoryBalanceTotals currentBalance(Long materialId, Long warehouseId) {
        MapSqlParameterSource params = new MapSqlParameterSource("materialId", materialId)
                .addValue("warehouseId", warehouseId == null
                        ? InventoryBalanceSnapshotRepository.NO_WAREHOUSE
                        : warehouseId);
        List<InventoryBalanceTotals> rows = jdbcTemplate.query(
                BALANCE_SQL,
                params,
                (rs, rowNum) -> new InventoryBalanceTotals(rs.getLong("quantity"), rs.getBigDecimal("amount")));
        return rows.isEmpty() ? InventoryBalanceTotals.EMPTY : rows.get(0);
    }
}
