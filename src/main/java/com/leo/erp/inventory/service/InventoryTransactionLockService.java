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
 * <p>统一加锁协议（<b>顺序不可调换</b>）：</p>
 * <ol>
 *   <li>库存写事务先取 {@code inv:backfill} 的<b>事务级共享锁</b>
 *       （{@link #lockBackfillSharedTxScoped()}）——与余额重建/期初回填的<b>排他锁</b>互斥；</li>
 *   <li>再对一组库存维度通过 {@link #lockAll(Collection)} 按 (materialId, warehouseId) 全局升序
 *       获取排他锁，同一事务内重复获取同一维度为可重入。</li>
 * </ol>
 * <p>先全局后维度的固定顺序 + 所有调用方共用同一维度升序，消除了常规记账与期初回填之间的
 * AB-BA 死锁；全局共享锁与排他锁的互斥则保证「按账本整表覆盖写」的余额重建不会吞掉
 * 并发已提交的增量记账。</p>
 */
@Component
public class InventoryTransactionLockService {

    private static final String LOCK_SQL =
            "SELECT pg_advisory_xact_lock(CAST(hashtext(:lockKey) AS bigint))";
    private static final String SHARED_LOCK_SQL =
            "SELECT pg_advisory_xact_lock_shared(CAST(hashtext(:lockKey) AS bigint))";
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
     * 库存维护操作的<b>事务级</b>互斥锁：作用域与当前事务一致，提交/回滚时自动释放。
     *
     * <p>供 {@code InventoryBalanceMaintenanceService.rebuild()} 这类<b>单事务内完成</b>的
     * 短操作使用。与 {@link #tryLockBackfill()} 的区别是生命周期与阻塞语义：
     * 前者随事务、会等待；后者跨事务、立即返回。不要混用 —— 回填是分批多事务的，
     * 用事务级锁会在第一批提交时就释放，等于没有互斥。</p>
     */
    public void lockBackfillTxScoped() {
        acquire(BACKFILL_LOCK_KEY);
    }

    /**
     * 记账路径的<b>事务级共享锁</b>：与余额重建/期初回填的排他锁（同一 {@code inv:backfill} 键）
     * 互斥，但多个记账事务之间兼容（共享锁并发持有）。
     *
     * <p><b>为什么必须有：</b>余额重建按账本聚合值整表覆盖写 {@code inv_balance}，只拿维度锁的
     * 记账事务与它之间没有任何互斥——重建 SELECT 之后、UPSERT 之前提交的记账会被覆盖丢失，
     * 反之可能被重复计入。记账事务在此共享锁内完成「读余额 + 写账本 + 增量写快照」，
     * 重建的排他锁必须等所有在途记账提交后才获得，因此不会吞掉已提交记账。</p>
     *
     * <p><b>调用顺序：</b>必须在 {@link #lockAll(Collection)} 之前调用（先全局后维度），
     * 与重建/回填的加锁顺序一致，避免交叉等待。</p>
     *
     * <p><b>与期初回填的关系：</b>回填持有的是会话级排他锁；同一会话内再取共享锁按 PostgreSQL
     * 咨询锁语义直接成功，不会自阻塞，因此回填批次内的记账调用可以安全经过本方法。</p>
     */
    public void lockBackfillSharedTxScoped() {
        acquireShared(BACKFILL_LOCK_KEY);
    }

    /**
     * 库存期初回填全局互斥锁（<b>会话级、非阻塞</b>）。
     *
     * <p>用 {@code pg_try_advisory_lock} 而不是事务级的 {@code pg_advisory_xact_lock}：</p>
     * <ul>
     *   <li>回填现在是分批短事务（见 {@code InventoryBackfillService.backfill}），
     *       事务级锁随每批提交就释放，起不到「整轮回填互斥」的作用；</li>
     *   <li>会话级锁的持有者是数据库连接，能跨多个批次事务持续有效；</li>
     *   <li>用 try 而非等待：并发的第二次回填立即失败返回，不会占着连接排队。</li>
     * </ul>
     *
     * <p><b>注意</b>：会话级锁必须显式 {@link #unlockBackfill()} 释放，
     * 且释放与加锁必须落在<b>同一条数据库连接</b>上。因此调用方需保证
     * tryLock 与 unlock 之间不发生连接切换（本仓库通过 {@code DataSourceUtils}
     * 复用同一连接；若将来引入多数据源或连接池重绑定，此约束需重新评估）。</p>
     *
     * @return true 表示拿到锁；false 表示已有回填在执行
     */
    public boolean tryLockBackfill() {
        MapSqlParameterSource params = new MapSqlParameterSource("lockKey", BACKFILL_LOCK_KEY);
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(CAST(hashtext(:lockKey) AS bigint))",
                params,
                Boolean.class);
        return Boolean.TRUE.equals(acquired);
    }

    /** 释放 {@link #tryLockBackfill()} 取得的会话级锁；未持有时为无操作。 */
    public void unlockBackfill() {
        MapSqlParameterSource params = new MapSqlParameterSource("lockKey", BACKFILL_LOCK_KEY);
        jdbcTemplate.query(
                "SELECT pg_advisory_unlock(CAST(hashtext(:lockKey) AS bigint))",
                params,
                rs -> {
                });
    }

    private static long sortKey(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }

    private void acquire(String lockKey) {
        MapSqlParameterSource params = new MapSqlParameterSource("lockKey", lockKey);
        jdbcTemplate.query(LOCK_SQL, params, rs -> {
        });
    }

    private void acquireShared(String lockKey) {
        MapSqlParameterSource params = new MapSqlParameterSource("lockKey", lockKey);
        jdbcTemplate.query(SHARED_LOCK_SQL, params, rs -> {
        });
    }
}
