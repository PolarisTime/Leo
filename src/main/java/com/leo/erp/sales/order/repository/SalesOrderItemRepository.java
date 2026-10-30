package com.leo.erp.sales.order.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {

    /**
     * 销售订单软删除时解除本单明细对上游明细（采购订单明细 / 采购入库明细）的引用
     * （与 V203/V204 同口径）。
     *
     * <p>删除是软删除（只置 {@code deleted_flag}，明细行保留），而明细上的两个来源列通过
     * 外键物理锁定上游明细；若不解绑，「已删除的销售订单」会继续阻止采购订单/采购入库改单。
     * 两个来源列在同一条 UPDATE 里置空：软删除的单据两条来源都不再成立。</p>
     *
     * @return 受影响行数，仅用于日志；0 行不是错误
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update so_sales_order_item
               set source_purchase_order_item_id = null,
                   source_inbound_item_id = null
             where order_id = :orderId
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedOrder(@Param("orderId") Long orderId);

    @Query("""
            select item
            from SalesOrderItem item
            join fetch item.salesOrder salesOrder
            where salesOrder.deletedFlag = false
              and item.id in :itemIds
            """)
    List<SalesOrderItem> findActiveByIdIn(@Param("itemIds") Collection<Long> itemIds);

    @Query("""
            select item
            from SalesOrderItem item
            join fetch item.salesOrder salesOrder
            where salesOrder.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
            """)
    List<SalesOrderItem> findActiveBySourcePurchaseOrderItemIds(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds
    );

    @Query("""
            select item
            from SalesOrderItem item
            join fetch item.salesOrder salesOrder
            where salesOrder.deletedFlag = false
              and item.sourceInboundItemId in :sourceInboundItemIds
            """)
    List<SalesOrderItem> findActiveBySourceInboundItemIds(
            @Param("sourceInboundItemIds") Collection<Long> sourceInboundItemIds
    );

    @Query("""
            select distinct item.sourceInboundItemId
            from SalesOrderItem item
            where item.sourceInboundItemId in :sourceInboundItemIds
            """)
    List<Long> findReferencedSourceInboundItemIds(
            @Param("sourceInboundItemIds") Collection<Long> sourceInboundItemIds
    );

    @Query("""
            select distinct outboundItem.sourceSalesOrderItemId
            from SalesOutboundItem outboundItem
            join outboundItem.salesOutbound outbound
            where outbound.deletedFlag = false
              and outboundItem.sourceSalesOrderItemId in :sourceSalesOrderItemIds
              and (:currentOutboundId is null or outbound.id <> :currentOutboundId)
            """)
    List<Long> findOccupiedSourceSalesOrderItemIds(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds,
            @Param("currentOutboundId") Long currentOutboundId
    );

    @Query("""
            select item.sourceInboundItemId as sourceInboundItemId,
                   sum(item.quantity) as totalQuantity,
                   coalesce(sum(item.weightTon), 0) as totalWeightTon
            from SalesOrderItem item
            join item.salesOrder salesOrder
            where salesOrder.deletedFlag = false
              and item.sourceInboundItemId in :sourceInboundItemIds
              and (:currentOrderId is null or salesOrder.id <> :currentOrderId)
            group by item.sourceInboundItemId
            """)
    List<SourceInboundAllocationSummary> summarizeAllocatedQuantityBySourceInboundItemIds(
            @Param("sourceInboundItemIds") Collection<Long> sourceInboundItemIds,
            @Param("currentOrderId") Long currentOrderId
    );

    @Query("""
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   sum(item.quantity) as totalQuantity,
                   coalesce(sum(item.weightTon), 0) as totalWeightTon
            from SalesOrderItem item
            join item.salesOrder salesOrder
            where salesOrder.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
              and (:currentOrderId is null or salesOrder.id <> :currentOrderId)
            group by item.sourcePurchaseOrderItemId
            """)
    List<SourcePurchaseOrderAllocationSummary> summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds,
            @Param("currentOrderId") Long currentOrderId
    );

    interface SourceInboundAllocationSummary {

        Long getSourceInboundItemId();

        Long getTotalQuantity();

        java.math.BigDecimal getTotalWeightTon();
    }

    interface SourcePurchaseOrderAllocationSummary {

        Long getSourcePurchaseOrderItemId();

        Long getTotalQuantity();

        java.math.BigDecimal getTotalWeightTon();
    }
}
