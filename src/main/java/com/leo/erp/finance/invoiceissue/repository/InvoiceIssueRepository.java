package com.leo.erp.finance.invoiceissue.repository;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface InvoiceIssueRepository extends JpaRepository<InvoiceIssue, Long>, JpaSpecificationExecutor<InvoiceIssue> {

    boolean existsByIssueNoAndDeletedFlagFalse(String issueNo);

    @EntityGraph(attributePaths = "items")
    List<InvoiceIssue> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<InvoiceIssue> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select item.sourceSalesOrderItemId as sourceSalesOrderItemId,
                   coalesce(sum(item.weightTon), 0) as totalWeightTon,
                   coalesce(sum(item.amount), 0) as totalAmount
            from InvoiceIssue issue
            join issue.items item
            where issue.deletedFlag = false
              and item.sourceSalesOrderItemId in :sourceItemIds
              and (:currentIssueId is null or issue.id <> :currentIssueId)
            group by item.sourceSalesOrderItemId
            """)
    List<SourceAllocationSummary> summarizeAllocatedBySourceSalesOrderItemIds(
            @Param("sourceItemIds") Collection<Long> sourceItemIds,
            @Param("currentIssueId") Long currentIssueId
    );

    interface SourceAllocationSummary {

        Long getSourceSalesOrderItemId();

        BigDecimal getTotalWeightTon();

        BigDecimal getTotalAmount();
    }
}
