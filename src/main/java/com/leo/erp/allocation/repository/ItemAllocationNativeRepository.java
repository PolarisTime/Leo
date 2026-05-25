package com.leo.erp.allocation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 共享的明细分配查询 Repository，使用原生 SQL 仅依赖 source_*_item_id 列，
 * 不引用任何业务模块实体，消除 purchase ↔ sales 循环依赖。
 */
public interface ItemAllocationNativeRepository extends JpaRepository<AllocationDummy, Long> {

    /** 查询指定采购订单明细在销售订单中的分配量 */
    @Query(value = """
            SELECT si.source_purchase_order_item_id AS source_item_id,
                   SUM(si.quantity)                  AS total_quantity,
                   COALESCE(SUM(si.weight_ton), 0)   AS total_weight_ton
              FROM so_sales_order_item si
              JOIN so_sales_order so ON so.id = si.order_id AND so.deleted_flag = FALSE
             WHERE si.source_purchase_order_item_id IN (:ids)
               AND (:exclude_order_id IS NULL OR si.order_id <> :exclude_order_id)
             GROUP BY si.source_purchase_order_item_id
            """, nativeQuery = true)
    List<AllocationProjection> summarizeSalesByPurchaseOrderItems(
            @Param("ids") Collection<Long> ids,
            @Param("exclude_order_id") Long excludeOrderId);

    /** 查询指定采购订单明细在采购入库中的分配量 */
    @Query(value = """
            SELECT pi.source_purchase_order_item_id AS source_item_id,
                   SUM(pi.quantity)                  AS total_quantity,
                   COALESCE(SUM(pi.weight_ton), 0)   AS total_weight_ton
              FROM po_purchase_inbound_item pi
              JOIN po_purchase_inbound inbound ON inbound.id = pi.inbound_id AND inbound.deleted_flag = FALSE
             WHERE pi.deleted_flag = FALSE
               AND pi.source_purchase_order_item_id IN (:ids)
               AND (:exclude_inbound_id IS NULL OR pi.inbound_id <> :exclude_inbound_id)
             GROUP BY pi.source_purchase_order_item_id
            """, nativeQuery = true)
    List<AllocationProjection> summarizeInboundByPurchaseOrderItems(
            @Param("ids") Collection<Long> ids,
            @Param("exclude_inbound_id") Long excludeInboundId);

    /** 查询指定采购入库明细在销售订单中的分配量 */
    @Query(value = """
            SELECT si.source_inbound_item_id         AS source_item_id,
                   SUM(si.quantity)                  AS total_quantity,
                   COALESCE(SUM(si.weight_ton), 0)   AS total_weight_ton
              FROM so_sales_order_item si
              JOIN so_sales_order so ON so.id = si.order_id AND so.deleted_flag = FALSE
             WHERE si.source_inbound_item_id IN (:ids)
               AND (:exclude_order_id IS NULL OR si.order_id <> :exclude_order_id)
             GROUP BY si.source_inbound_item_id
            """, nativeQuery = true)
    List<AllocationProjection> summarizeSalesByInboundItems(
            @Param("ids") Collection<Long> ids,
            @Param("exclude_order_id") Long excludeOrderId);

    /** 查询指定采购订单明细的重量调整量 */
    @Query(value = """
            SELECT pi.source_purchase_order_item_id AS source_item_id,
                   COALESCE(SUM(pi.weight_adjustment_ton), 0) AS total_weight_ton
              FROM po_purchase_inbound_item pi
              JOIN po_purchase_inbound inbound ON inbound.id = pi.inbound_id AND inbound.deleted_flag = FALSE
             WHERE pi.deleted_flag = FALSE
               AND pi.source_purchase_order_item_id IN (:ids)
             GROUP BY pi.source_purchase_order_item_id
            """, nativeQuery = true)
    List<AllocationProjection> summarizeWeightAdjustmentByPurchaseOrderItems(
            @Param("ids") Collection<Long> ids);

    interface AllocationProjection {
        Long getSourceItemId();
        Long getTotalQuantity();
        java.math.BigDecimal getTotalWeightTon();
    }
}
