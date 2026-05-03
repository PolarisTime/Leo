package com.leo.erp.sales.order.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {

    @Query("""
            select item
            from SalesOrderItem item
            join item.salesOrder salesOrder
            where salesOrder.deletedFlag = false
              and item.id in :itemIds
            """)
    List<SalesOrderItem> findActiveByIdIn(@Param("itemIds") Collection<Long> itemIds);

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
