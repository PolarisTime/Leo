package com.leo.erp.inventory.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 库存余额聚合读取器：按 (material_id, warehouse_id) 聚合未删除库存事务。
 * 余额不落表，出库/退货记账前实时读取。
 */
@Component
public class InventoryBalanceReader {

    private static final String BALANCE_SELECT = """
            SELECT COALESCE(SUM(quantity * direction), 0) AS quantity,
                   COALESCE(SUM(amount), 0) AS amount
            FROM inv_transaction
            WHERE deleted_flag = false
              AND material_id = :materialId
            """;

    private static final String BALANCE_BY_WAREHOUSE_SQL = BALANCE_SELECT + " AND warehouse_id = :warehouseId";
    private static final String BALANCE_NO_WAREHOUSE_SQL = BALANCE_SELECT + " AND warehouse_id IS NULL";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryBalanceReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 读取当前库存余额；无任何事务时返回 {@link InventoryBalanceTotals#EMPTY}。
     *
     * @param warehouseId 仓库ID，可为空表示无仓库维度
     */
    public InventoryBalanceTotals currentBalance(Long materialId, Long warehouseId) {
        MapSqlParameterSource params = new MapSqlParameterSource("materialId", materialId);
        String sql = BALANCE_NO_WAREHOUSE_SQL;
        if (warehouseId != null) {
            sql = BALANCE_BY_WAREHOUSE_SQL;
            params.addValue("warehouseId", warehouseId);
        }
        return jdbcTemplate.queryForObject(
                sql,
                params,
                (rs, rowNum) -> new InventoryBalanceTotals(
                        rs.getLong("quantity"),
                        rs.getBigDecimal("amount")
                )
        );
    }
}
