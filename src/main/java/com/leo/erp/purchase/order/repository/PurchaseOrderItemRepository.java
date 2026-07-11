package com.leo.erp.purchase.order.repository;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
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

    @Query("""
            select item.id
            from PurchaseOrderItem item
            join item.purchaseOrder purchaseOrder
            where purchaseOrder.id = :purchaseOrderId
              and purchaseOrder.deletedFlag = false
            order by item.id
            """)
    List<Long> findActiveIdsByPurchaseOrderId(@Param("purchaseOrderId") Long purchaseOrderId);
}
