package com.leo.erp.inventory.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;

/**
 * 库存维度事务锁：对 (material_id, warehouse_id) 获取 PostgreSQL 事务级咨询锁，
 * 串行化同一库存维度的余额读取与记账写入，避免并发下移动加权成本与余额错乱。
 *
 * <p>统一加锁协议：所有调用方对一组库存维度一律通过 {@link #lockAll(Collection)} 按
 * (materialId, warehouseId) 全局升序获取，且同一事务内重复获取同一维度为可重入，
 * 从而消除常规记账与期初回填之间的 AB-BA 死锁。
 */
@Component
public class InventoryTransactionLockService {

    private static final String LOCK_SQL =
            "SELECT pg_advisory_xact_lock(CAST(hashtext(:lockKey) AS bigint))";
    private static final String BACKFILL_LOCK_KEY = "inv:backfill";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryTransactionLockService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 库存维度：加锁与排序统一以 (materialId, warehouseId) 为准，warehouseId 允许为空。
     */
    public record Dimension(Long materialId, Long warehouseId) {
    }

    /**
     * 对单个库存维度加事务级排他咨询锁，随当前事务提交/回滚自动释放。可重入。
     */
    public void lock(Long materialId, Long warehouseId) {
        String lockKey = "inv:" + materialId + ":" + (warehouseId == null ? "0" : warehouseId);
        acquire(lockKey);
    }

    /**
     * 对一组库存维度按 (materialId, warehouseId) 全局升序（空值排最后）依次加锁。
     * 所有调用方共用该顺序，保证并发事务加锁序列一致，避免交叉等待死锁。
     */
    public void lockAll(Collection<Dimension> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return;
        }
        dimensions.stream()
                .filter(dimension -> dimension != null && dimension.materialId() != null)
                .distinct()
                .sorted(Comparator
                        .comparing((Dimension dimension) -> sortKey(dimension.materialId()))
                        .thenComparing(dimension -> sortKey(dimension.warehouseId())))
                .forEach(dimension -> lock(dimension.materialId(), dimension.warehouseId()));
    }

    /**
     * 库存期初回填全局串行锁：保证同一时刻只有一个回填事务扫描/记账，
     * 避免并发回填对同一来源明细重复记账。
     */
    public void lockBackfill() {
        acquire(BACKFILL_LOCK_KEY);
    }

    private static long sortKey(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }

    private void acquire(String lockKey) {
        MapSqlParameterSource params = new MapSqlParameterSource("lockKey", lockKey);
        jdbcTemplate.query(LOCK_SQL, params, rs -> {
        });
    }
}
