package com.leo.erp.purchase.order.repository;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    @Query("""
            select item
            from PurchaseOrderItem item
            join fetch item.purchaseOrder purchaseOrder
            where purchaseOrder.deletedFlag = false
              and item.id in :itemIds
            """)
    List<PurchaseOrderItem> findActiveByIdIn(@Param("itemIds") Collection<Long> itemIds);

    /**
     * 快照读取：<b>刻意不过滤 {@code purchaseOrder.deletedFlag}</b>。
     *
     * <p>唯一调用方是 {@code PurchaseItemQueryAppService#findPurchaseOrderItemSnapshotsByIds}，
     * 再由 {@code SalesOrderSourceAllocationService#loadLegacyPurchaseOrderNos} 用来给
     * 「历史采购订单直连来源」解析订单号（快照语义：来源单据之后可能被归档/删除，仍要能读出编号）。</p>
     *
     * <p>这样做不会放过「用已删除来源新建单据」：活跃销售订单引用采购订单明细时，
     * {@code PurchaseOrderDownstreamMutationGuard#assertMutable}（经
     * {@code PurchaseOrderReferenceGuard#hasActivePurchaseOrderItemReferences}）会阻止该采购订单被删除；
     * 而新行也不允许再使用采购订单直连来源（见 {@code SalesOrderSourceAllocationService#validateSourceShape}）。
     * 若将来需要「只取未删除来源」，请改用 {@link #findActiveByIdIn}，不要在这里加过滤——那会让快照解析失效。</p>
     */
    @Query("""
            select item
            from PurchaseOrderItem item
            join fetch item.purchaseOrder
            where item.id in :itemIds
            """)
    List<PurchaseOrderItem> findSnapshotsByIdIn(@Param("itemIds") Collection<Long> itemIds);

    @Query("""
            select item.id
            from PurchaseOrderItem item
            join item.purchaseOrder purchaseOrder
            where purchaseOrder.id = :purchaseOrderId
              and purchaseOrder.deletedFlag = false
            order by item.id
            """)
    List<Long> findActiveIdsByPurchaseOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 查询未删除采购订单的明细行(含订单快照), 供报单比价按规格关联。
     * <p>关键字匹配订单号/供应商/类别/材质/规格/长度; 可按订单与状态过滤;
     * 按订单倒序、订单内行号升序。</p>
     * <p>已强制结单的订单不再作为可开吨位来源: 其剩余吨位已随剩余件数一起作废。</p>
     */
    @Query("""
            select item
            from PurchaseOrderItem item
            join fetch item.purchaseOrder purchaseOrder
            where purchaseOrder.deletedFlag = false
              and purchaseOrder.forceClosed = false
              and (:status is null or purchaseOrder.status = :status)
              and (:purchaseOrderId is null or purchaseOrder.id = :purchaseOrderId)
              and (:keyword is null
                   or lower(purchaseOrder.orderNo) like :keyword
                   or lower(purchaseOrder.supplierName) like :keyword
                   or lower(item.category) like :keyword
                   or lower(item.material) like :keyword
                   or lower(item.spec) like :keyword
                   or lower(item.length) like :keyword)
            order by purchaseOrder.id desc, item.lineNo asc
            """)
    List<PurchaseOrderItem> findActiveItemOptions(@Param("keyword") String keyword,
                                                  @Param("status") String status,
                                                  @Param("purchaseOrderId") Long purchaseOrderId,
                                                  Pageable pageable);

    /** 按明细行 id 批量查询未删除订单的明细行(含订单快照)。 */
    @Query("""
            select item
            from PurchaseOrderItem item
            join fetch item.purchaseOrder purchaseOrder
            where purchaseOrder.deletedFlag = false
              and item.id in :itemIds
            """)
    List<PurchaseOrderItem> findActiveItemOptionsByIdIn(@Param("itemIds") Collection<Long> itemIds);
}
