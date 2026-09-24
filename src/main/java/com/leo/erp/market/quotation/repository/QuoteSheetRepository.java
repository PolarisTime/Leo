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
     * <p>仅统计 {@code purchaseOrderItemId} 非空的商品行; 未回填的历史行不参与行级口径。</p>
     *
     * @return 每行 {@code [purchaseOrderItemId(Long), totalTon(BigDecimal or null)]}
     */
    @Query("""
            select item.purchaseOrderItemId, sum(item.ton)
            from QuoteSheetItem item
            where item.purchaseOrderItemId in :purchaseOrderItemIds
              and item.sheet.deletedFlag = false
              and (:excludeSheetId is null or item.sheet.id <> :excludeSheetId)
            group by item.purchaseOrderItemId
            """)
    List<Object[]> sumIssuedTonByPurchaseOrderItemIds(@Param("purchaseOrderItemIds") Collection<Long> purchaseOrderItemIds,
                                                      @Param("excludeSheetId") Long excludeSheetId);
}
