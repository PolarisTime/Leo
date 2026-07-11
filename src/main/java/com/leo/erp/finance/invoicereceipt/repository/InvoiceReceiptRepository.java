package com.leo.erp.finance.invoicereceipt.repository;

import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceReceiptRepository extends JpaRepository<InvoiceReceipt, Long>, JpaSpecificationExecutor<InvoiceReceipt> {

    boolean existsByReceiveNoAndDeletedFlagFalse(String receiveNo);

    @EntityGraph(attributePaths = "items")
    List<InvoiceReceipt> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<InvoiceReceipt> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   coalesce(sum(item.quantity), 0) as totalQuantity,
                   coalesce(sum(item.weightTon), 0) as totalWeightTon,
                   coalesce(sum(item.amount), 0) as totalAmount
            from InvoiceReceipt receipt
            join receipt.items item
            where receipt.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourceItemIds
              and (:currentReceiptId is null or receipt.id <> :currentReceiptId)
            group by item.sourcePurchaseOrderItemId
            """)
    List<SourceAllocationSummary> summarizeAllocatedBySourcePurchaseOrderItemIds(
            @Param("sourceItemIds") Collection<Long> sourceItemIds,
            @Param("currentReceiptId") Long currentReceiptId
    );

    @Query("""
            select item.sourcePurchaseOrderItemId as sourcePurchaseOrderItemId,
                   coalesce(sum(item.quantity), 0) as totalQuantity,
                   coalesce(sum(item.weightTon), 0) as totalWeightTon,
                   coalesce(sum(item.amount), 0) as totalAmount
            from PurchaseRefundItem item
            join item.purchaseRefund refund
            where refund.deletedFlag = false
              and refund.status = '已审核'
              and item.sourcePurchaseOrderItemId in :sourceItemIds
            group by item.sourcePurchaseOrderItemId
            """)
    List<SourceAllocationSummary> summarizeAuditedRefundBySourcePurchaseOrderItemIds(
            @Param("sourceItemIds") Collection<Long> sourceItemIds
    );

    @Query("""
            select distinct item.sourcePurchaseOrderItemId
            from InvoiceReceipt receipt
            join receipt.items item
            where receipt.deletedFlag = false
              and receipt.status = :status
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
            """)
    List<Long> findSourcePurchaseOrderItemIdsByStatus(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds,
            @Param("status") String status
    );

    interface SourceAllocationSummary {

        Long getSourcePurchaseOrderItemId();

        Long getTotalQuantity();

        BigDecimal getTotalWeightTon();

        BigDecimal getTotalAmount();
    }
}
