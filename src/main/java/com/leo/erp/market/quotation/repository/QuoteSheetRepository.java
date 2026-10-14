package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuoteSheetRepository extends JpaRepository<QuoteSheet, Long>,
        JpaSpecificationExecutor<QuoteSheet> {

    boolean existsBySheetNoAndDeletedFlagFalse(String sheetNo);

    /** 只读取版本号(不初始化 brands/items 集合), 供写事务提交后回读权威版本。 */
    @Query("select sheet.version from QuoteSheet sheet where sheet.id = :id and sheet.deletedFlag = false")
    Long findVersionById(@Param("id") Long id);

    /**
     * 读取详情/行级写父单据。
     * <p>
     * 只 fetch join {@code items} 这一个 bag; {@code brands} 与 {@code items.prices} 同为 bag,
     * 任何两个 bag(Hibernate 6.6 下含"父 bag + 嵌套 bag")同时 fetch join 都会触发
     * {@code MultipleBagFetchException} → {@code JpaSystemException} → 生产 500。
     * 因此这两个集合保持 LAZY, 由调用方在同一事务内按需初始化; 集合已配置
     * {@code @BatchSize(50)}(并叠加全局 {@code default_batch_fetch_size=50})批量抓取, 无 N+1。
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<QuoteSheet> findByIdAndDeletedFlagFalse(Long id);

    /**
     * 加数据库行级排他锁读取存在的报价单。用于编辑签出锁的首次插入串行化:
     * {@code mk_quote_sheet_edit_lock.sheet_id} 上对不存在行做 {@code FOR UPDATE} 不会加锁,
     * 因此先锁父单据行, 让同一单据的签出/抢占在多副本下严格串行, 避免唯一键冲突。
     * <p>锁语义: 使用阻塞式 {@code FOR UPDATE}(不设置 NOWAIT)。原先的 {@code lock.timeout=0}(NOWAIT)
     * 会让同一单据的正常并发签出/续约立即抛 {@code CannotAcquireLockException} 并映射 409, 体验回归;
     * 签出相关事务都是单行、短事务, 阻塞等待时间极短。若等待时间过长, 由数据库 {@code lock_timeout}
     * (例如 {@code LEO_POSTGRES_LOCK_TIMEOUT=10s}) 取消并冒泡为 {@code CannotAcquireLockException},
     * 再由全局异常统一映射 409(CONCURRENT_MODIFICATION), 不会暴露为 500。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sheet from QuoteSheet sheet where sheet.id = :id and sheet.deletedFlag = false")
    Optional<QuoteSheet> findActiveForUpdate(@Param("id") Long id);

    /**
     * 按采购订单明细行汇总报单已开吨位: 按规格扣减的唯一真源。
     *
     * <p><b>必须是一条语句(单快照)</b>: 口径由两段组成 —— 有明细行关联时按 {@code allocated_ton} 计
     * (一行 130 吨拆到两张明细行 60+70 时, 两张各计自己那一份, 不能各计 130, 否则同一张单据会把
     * 两张明细行的额度都吃掉); 没有关联行的历史数据仍按旧单值列 {@code mk_quote_item.purchase_order_item_id}
     * + 整行吨位计(迁移回填前的兼容口径)。两段若拆成两条查询再在内存相加, READ COMMITTED 下另一事务
     * 在两条语句之间提交(正是"旧单值行 + 回填/改写关联表"这类转换), 该行会两段都不计而瞬时少计;
     * 合并成单条 SQL 后由数据库保证整条语句只用一个快照。</p>
     *
     * <p>用 {@code UNION ALL} 而不是带 {@code or} 的联表: 两个分支各自按
     * {@code purchase_order_item_id} 走索引(见 Crunchy Data 的 LEFT JOIN vs UNION ALL 实测),
     * {@code or} 联表会让 PostgreSQL 无法分别用索引。{@code excludeSheetId} 显式 {@code cast}
     * 成 bigint, 避免 null 时 PostgreSQL 无法推断参数类型。</p>
     *
     * @return 每行 {@code [purchaseOrderItemId(Long), totalTon(BigDecimal or null)]}
     */
    @Query(value = """
            SELECT issued.purchase_order_item_id, SUM(issued.ton)
              FROM (
                    SELECT link.purchase_order_item_id AS purchase_order_item_id,
                           link.allocated_ton AS ton
                      FROM mk_quote_item_purchase_order link
                      JOIN mk_quote_item item ON item.id = link.item_id
                      JOIN mk_quote_sheet sheet ON sheet.id = item.sheet_id
                     WHERE link.purchase_order_item_id IN (:purchaseOrderItemIds)
                       AND sheet.deleted_flag = false
                       AND (CAST(:excludeSheetId AS bigint) IS NULL
                            OR sheet.id <> CAST(:excludeSheetId AS bigint))
                    UNION ALL
                    SELECT item.purchase_order_item_id AS purchase_order_item_id,
                           item.ton AS ton
                      FROM mk_quote_item item
                      JOIN mk_quote_sheet sheet ON sheet.id = item.sheet_id
                     WHERE item.purchase_order_item_id IN (:purchaseOrderItemIds)
                       AND NOT EXISTS (
                            SELECT 1 FROM mk_quote_item_purchase_order link
                             WHERE link.item_id = item.id)
                       AND sheet.deleted_flag = false
                       AND (CAST(:excludeSheetId AS bigint) IS NULL
                            OR sheet.id <> CAST(:excludeSheetId AS bigint))
                   ) issued
             GROUP BY issued.purchase_order_item_id
            """, nativeQuery = true)
    List<Object[]> sumIssuedTonByPurchaseOrderItemIds(
            @Param("purchaseOrderItemIds") Collection<Long> purchaseOrderItemIds,
            @Param("excludeSheetId") Long excludeSheetId);
}
