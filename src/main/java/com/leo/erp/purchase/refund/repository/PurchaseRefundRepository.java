package com.leo.erp.purchase.refund.repository;

import com.leo.erp.purchase.refund.domain.entity.PurchaseRefund;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseRefundRepository extends JpaRepository<PurchaseRefund, Long>,
        JpaSpecificationExecutor<PurchaseRefund> {

    boolean existsByRefundNoAndDeletedFlagFalse(String refundNo);

    boolean existsBySourcePurchaseOrderIdAndDeletedFlagFalse(Long sourcePurchaseOrderId);

    boolean existsBySourcePurchaseOrderIdAndDeletedFlagFalseAndIdNot(Long sourcePurchaseOrderId, Long id);

    @Query("""
            select distinct refund.sourcePurchaseOrderId
            from PurchaseRefund refund
            where refund.deletedFlag = false
              and refund.sourcePurchaseOrderId in :sourcePurchaseOrderIds
            """)
    List<Long> findActiveSourcePurchaseOrderIdsBySourcePurchaseOrderIdIn(
            @Param("sourcePurchaseOrderIds") Collection<Long> sourcePurchaseOrderIds
    );

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseRefund> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   sum(item.quantity) as totalQuantity
            from PurchaseRefundItem item
            join item.purchaseRefund refund
            where refund.deletedFlag = false
              and refund.status = '已审核'
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
            group by item.sourcePurchaseOrderItemId
            """)
    List<PurchaseOrderRefundQuantitySummary> summarizeAuditedQuantityBySourcePurchaseOrderItemIds(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds
    );

    interface PurchaseOrderRefundQuantitySummary {

        Long getSourcePurchaseOrderItemId();

        Long getTotalQuantity();
    }
}
