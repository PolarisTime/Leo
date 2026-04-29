package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   sum(item.quantity) as totalQuantity
            from PurchaseInboundItem item
            join item.purchaseInbound inbound
            where inbound.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
            group by item.sourcePurchaseOrderItemId
            """)
    List<PurchaseOrderAllocationSummary> summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds
    );

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

    interface PurchaseOrderAllocationSummary {

        Long getSourcePurchaseOrderItemId();

        Long getTotalQuantity();
    }

    interface PurchaseOrderWeightAdjustmentSummary {

        Long getSourcePurchaseOrderItemId();

        java.math.BigDecimal getTotalWeightAdjustmentTon();
    }
}
