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

    interface SourceAllocationSummary {

        Long getSourcePurchaseOrderItemId();

        BigDecimal getTotalWeightTon();

        BigDecimal getTotalAmount();
    }
}
