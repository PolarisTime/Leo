package com.leo.erp.inventory.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 库存维度事务锁：对 (material_id, warehouse_id) 获取 PostgreSQL 事务级咨询锁，
 * 串行化同一库存维度的余额读取与记账写入，避免并发下移动加权成本与余额错乱。
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
     * 对指定库存维度加事务级排他咨询锁，随当前事务提交/回滚自动释放。可重入。
     */
    public void lock(Long materialId, Long warehouseId) {
        String lockKey = "inv:" + materialId + ":" + (warehouseId == null ? "0" : warehouseId);
        acquire(lockKey);
    }

    /**
     * 库存期初回填全局串行锁：保证同一时刻只有一个回填事务扫描/记账，
     * 避免并发回填对同一来源明细重复记账。
     */
    public void lockBackfill() {
        acquire(BACKFILL_LOCK_KEY);
    }

    private void acquire(String lockKey) {
        MapSqlParameterSource params = new MapSqlParameterSource("lockKey", lockKey);
        jdbcTemplate.query(LOCK_SQL, params, rs -> null);
    }
}
