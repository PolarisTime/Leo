package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PurchaseInboundItemRepository extends JpaRepository<PurchaseInboundItem, Long> {

    @Query("""
            select item
            from PurchaseInboundItem item
            join fetch item.purchaseInbound inbound
            where inbound.deletedFlag = false
              and item.id in :ids
            """)
    List<PurchaseInboundItem> findAllActiveByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select item
            from PurchaseInboundItem item
            join fetch item.purchaseInbound inbound
            where inbound.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
            """)
    List<PurchaseInboundItem> findAllActiveBySourcePurchaseOrderItemIds(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds
    );

    /**
     * 解除「已软删除的采购入库单」对采购订单明细的来源引用。
     *
     * <p>采购入库单删除是软删除，明细行仍保留（审计需要），其 `source_purchase_order_item_id`
     * 通过 RESTRICT 外键物理锁定来源订单明细。已删除的单据在业务上不应再阻止订单修改，
     * 因此订单行被移除前先把这些残留引用置空，随后订单明细的物理删除才不会被外键拒绝。</p>
     *
     * <p>只处理 `deleted_flag = true` 的入库单：仍是活跃入库单的引用由业务守卫拦截，
     * 不会走到这里，保留 RESTRICT 作为最后一道防线。</p>
     *
     * @return 解除引用的残留明细行数（0 表示没有残留）
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update po_purchase_inbound_item item
               set source_purchase_order_item_id = null
              from po_purchase_inbound inbound
             where inbound.id = item.inbound_id
               and inbound.deleted_flag = true
               and item.source_purchase_order_item_id in (:sourceItemIds)
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedInbounds(
            @Param("sourceItemIds") Collection<Long> sourceItemIds
    );

    /**
     * 采购入库单软删除时解除本单明细对来源采购订单明细的引用（与 V203/V204 同口径）。
     *
     * <p>删除是软删除（只置 {@code deleted_flag}，明细行保留，审计需要），而明细上的
     * {@code source_purchase_order_item_id} 通过 RESTRICT 外键物理锁定上游订单明细；
     * 若不解绑，「已删除的入库单」会继续阻止采购订单改单。只解绑本单（按 {@code inbound_id}），
     * 活跃入库单的来源链路不受影响。</p>
     *
     * @return 受影响行数，仅用于日志；0 行不是错误
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update po_purchase_inbound_item
               set source_purchase_order_item_id = null
             where inbound_id = :inboundId
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedInbound(@Param("inboundId") Long inboundId);

    @Query("""
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   sum(item.quantity) as totalQuantity
            from PurchaseInboundItem item
            join item.purchaseInbound inbound
            where inbound.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
              and (:currentInboundId is null or inbound.id <> :currentInboundId)
            group by item.sourcePurchaseOrderItemId
            """)
    List<PurchaseOrderAllocationSummary> summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds,
            @Param("currentInboundId") Long currentInboundId
    );

    @Query("""
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   coalesce(sum(item.weightAdjustmentTon), 0) as totalWeightAdjustmentTon
            from PurchaseInboundItem item
            join item.purchaseInbound inbound
            where inbound.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
              and (:currentInboundId is null or inbound.id <> :currentInboundId)
            group by item.sourcePurchaseOrderItemId
            """)
    List<PurchaseOrderWeightAdjustmentSummary> summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds,
            @Param("currentInboundId") Long currentInboundId
    );

    @Query("""
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   sum(item.quantity) as totalQuantity,
                   coalesce(sum(coalesce(item.weighWeightTon, item.weightTon)), 0) as totalWeightTon
            from PurchaseInboundItem item
            join item.purchaseInbound inbound
            where inbound.deletedFlag = false
              and inbound.status in :effectiveStatuses
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
              and (:currentInboundId is null or inbound.id <> :currentInboundId)
            group by item.sourcePurchaseOrderItemId
            """)
    List<PurchaseOrderEffectiveWeightSummary> summarizeEffectiveWeightBySourcePurchaseOrderItemIdsExcludingInbound(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds,
            @Param("currentInboundId") Long currentInboundId,
            @Param("effectiveStatuses") Collection<String> effectiveStatuses
    );

    @Query("""
            select inbound.id as inboundId,
                   coalesce(sum(coalesce(item.weighWeightTon, item.weightTon)), 0) as totalWeighWeightTon,
                   coalesce(sum(item.weightAdjustmentTon), 0) as totalWeightAdjustmentTon
            from PurchaseInboundItem item
            join item.purchaseInbound inbound
            where inbound.deletedFlag = false
              and inbound.id in :inboundIds
            group by inbound.id
            """)
    List<InboundWeightSummary> summarizeWeightByInboundIds(
            @Param("inboundIds") Collection<Long> inboundIds
    );

    interface PurchaseOrderAllocationSummary {

        Long getSourcePurchaseOrderItemId();

        Long getTotalQuantity();
    }

    interface PurchaseOrderWeightAdjustmentSummary {

        Long getSourcePurchaseOrderItemId();

        java.math.BigDecimal getTotalWeightAdjustmentTon();
    }

    interface PurchaseOrderEffectiveWeightSummary {

        Long getSourcePurchaseOrderItemId();

        Long getTotalQuantity();

        java.math.BigDecimal getTotalWeightTon();
    }

    interface InboundWeightSummary {

        Long getInboundId();

        java.math.BigDecimal getTotalWeighWeightTon();

        java.math.BigDecimal getTotalWeightAdjustmentTon();
    }

    interface PurchaseOrderInvoiceCapacitySummary {

        Long getSourcePurchaseOrderItemId();

        Long getTotalQuantity();

        BigDecimal getTotalWeightTon();

        BigDecimal getTotalAmount();
    }
}
