package com.leo.erp.inventory.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存余额快照表 inv_balance 的数据访问。
 *
 * <p>写入只做增量 UPSERT（{@code quantity += direction*qty}、{@code amount += signedAmount}），
 * 必须由 {@link com.leo.erp.inventory.service.InventoryTransactionService} 在持有
 * (material_id, warehouse_id) 咨询锁的同一事务内调用，保证快照与账本守恒。
 *
 * <p>另提供从账本幂等重算的 {@link #rebuild()} 与守恒校验 {@link #findMismatches()}。
 */
@Repository
public class InventoryBalanceSnapshotRepository {

    /** 无仓库维度的哨兵值；雪花ID恒为正，0 永不与真实仓库冲突。 */
    public static final long NO_WAREHOUSE = 0L;

    private static final String UPSERT_SQL = """
            INSERT INTO inv_balance (
                material_id, warehouse_id, material_code, warehouse_name, batch_no,
                quantity, amount, updated_at
            )
            VALUES (
                :materialId, :warehouseId, :materialCode, :warehouseName, :batchNo,
                :quantityDelta,
                CASE WHEN :quantityDelta = 0 THEN 0 ELSE :amountDelta END,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (material_id, warehouse_id) DO UPDATE
            SET quantity = inv_balance.quantity + EXCLUDED.quantity,
                amount = CASE
                    WHEN inv_balance.quantity + EXCLUDED.quantity = 0 THEN 0
                    ELSE inv_balance.amount + EXCLUDED.amount
                END,
                material_code = COALESCE(EXCLUDED.material_code, inv_balance.material_code),
                warehouse_name = COALESCE(EXCLUDED.warehouse_name, inv_balance.warehouse_name),
                batch_no = COALESCE(EXCLUDED.batch_no, inv_balance.batch_no),
                updated_at = CURRENT_TIMESTAMP
            """;

    private static final String REBUILD_UPSERT_SQL = """
            INSERT INTO inv_balance (
                material_id, warehouse_id, material_code, warehouse_name, batch_no, quantity, amount
            )
            SELECT t.material_id,
                   COALESCE(t.warehouse_id, 0) AS warehouse_id,
                   MAX(t.material_code) AS material_code,
                   MAX(t.warehouse_name) AS warehouse_name,
                   MAX(t.batch_no) AS batch_no,
                   COALESCE(SUM(t.quantity * t.direction), 0) AS quantity,
                   CASE WHEN COALESCE(SUM(t.quantity * t.direction), 0) = 0 THEN 0
                        ELSE COALESCE(SUM(t.amount), 0) END AS amount
            FROM inv_transaction t
            WHERE t.deleted_flag = false
            GROUP BY t.material_id, COALESCE(t.warehouse_id, 0)
            ON CONFLICT (material_id, warehouse_id) DO UPDATE
            SET quantity = EXCLUDED.quantity,
                amount = EXCLUDED.amount,
                material_code = COALESCE(EXCLUDED.material_code, inv_balance.material_code),
                warehouse_name = COALESCE(EXCLUDED.warehouse_name, inv_balance.warehouse_name),
                batch_no = COALESCE(EXCLUDED.batch_no, inv_balance.batch_no),
                updated_at = CURRENT_TIMESTAMP
            """;

    private static final String REBUILD_DELETE_STALE_SQL = """
            DELETE FROM inv_balance b
            WHERE NOT EXISTS (
                SELECT 1
                FROM inv_transaction t
                WHERE t.deleted_flag = false
                  AND t.material_id = b.material_id
                  AND COALESCE(t.warehouse_id, 0) = b.warehouse_id
            )
            """;

    /**
     * 守恒校验：数量必须与账本严格一致；金额仅在数量非零时精确比较。
     *
     * <p>数量归零的维度<b>以快照为准、不比较金额</b>：数量归零时 {@link #applyDelta} 会把快照金额
     * 强制置 0，而账本会如实保留历史「出库移动加权均价 ≠ 对应入库单价」以及「出库业务日期早于入库」
     * 造成的价差残值（期初回填补记历史单据后必然出现）。该残值不参与任何可用量与后续移动加权成本
     * 计算（{@code InventoryBalanceReader#currentBalance} 对零数量维度返回 EMPTY），故不视为不一致。
     */
    private static final String RECONCILE_SQL = """
            SELECT COALESCE(l.material_id, b.material_id) AS material_id,
                   COALESCE(l.warehouse_id, b.warehouse_id) AS warehouse_id,
                   COALESCE(l.quantity, 0) AS ledger_quantity,
                   COALESCE(b.quantity, 0) AS snapshot_quantity,
                   COALESCE(l.amount, 0) AS ledger_amount,
                   COALESCE(b.amount, 0) AS snapshot_amount
            FROM (
                SELECT t.material_id,
                       COALESCE(t.warehouse_id, 0) AS warehouse_id,
                       SUM(t.quantity * t.direction) AS quantity,
                       SUM(t.amount) AS amount
                FROM inv_transaction t
                WHERE t.deleted_flag = false
                GROUP BY t.material_id, COALESCE(t.warehouse_id, 0)
            ) l
            FULL OUTER JOIN inv_balance b
                ON b.material_id = l.material_id AND b.warehouse_id = l.warehouse_id
            WHERE COALESCE(l.quantity, 0) <> COALESCE(b.quantity, 0)
               OR (
                    COALESCE(l.quantity, 0) <> 0
                    AND COALESCE(l.amount, 0)::numeric(14,2) <> COALESCE(b.amount, 0)::numeric(14,2)
                  )
            """;

    private static final RowMapper<BalanceMismatch> MISMATCH_MAPPER = (rs, rowNum) -> new BalanceMismatch(
            rs.getLong("material_id"),
            rs.getLong("warehouse_id"),
            rs.getLong("ledger_quantity"),
            rs.getLong("snapshot_quantity"),
            rs.getBigDecimal("ledger_amount"),
            rs.getBigDecimal("snapshot_amount"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryBalanceSnapshotRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 对单个 (material, warehouse) 维度增量更新余额；无快照行时插入。
     *
     * @param quantityDelta 带符号数量增量 direction * quantity
     * @param amountDelta   带符号金额增量（与账本 amount 同符号）
     */
    public void applyDelta(Long materialId,
                           Long warehouseId,
                           String materialCode,
                           String warehouseName,
                           String batchNo,
                           int quantityDelta,
                           BigDecimal amountDelta) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("materialId", materialId)
                .addValue("warehouseId", warehouseId == null ? NO_WAREHOUSE : warehouseId)
                .addValue("materialCode", materialCode)
                .addValue("warehouseName", warehouseName)
                .addValue("batchNo", batchNo)
                .addValue("quantityDelta", quantityDelta)
                .addValue("amountDelta", amountDelta == null ? BigDecimal.ZERO : amountDelta);
        jdbcTemplate.update(UPSERT_SQL, params);
    }

    /**
     * 从账本幂等重算快照：先按维度覆盖写回账本聚合值，再清理账本已无对应维度的过期行。
     *
     * @return 覆盖写入的维度行数
     */
    public int rebuild() {
        int upserted = jdbcTemplate.getJdbcTemplate().update(REBUILD_UPSERT_SQL);
        jdbcTemplate.getJdbcTemplate().update(REBUILD_DELETE_STALE_SQL);
        return upserted;
    }

    /**
     * 守恒校验：返回账本聚合与快照不一致的维度（数量不一致，或数量非零而金额不一致）。
     */
    public List<BalanceMismatch> findMismatches() {
        return jdbcTemplate.query(RECONCILE_SQL, new MapSqlParameterSource(), MISMATCH_MAPPER);
    }

    /**
     * 单个维度的账本/快照差异。
     */
    public record BalanceMismatch(
            long materialId,
            long warehouseId,
            long ledgerQuantity,
            long snapshotQuantity,
            BigDecimal ledgerAmount,
            BigDecimal snapshotAmount
    ) {
    }
}
