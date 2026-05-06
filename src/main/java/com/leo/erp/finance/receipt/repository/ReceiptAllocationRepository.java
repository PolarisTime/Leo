package com.leo.erp.finance.receipt.repository;

import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ReceiptAllocationRepository extends JpaRepository<ReceiptAllocation, Long> {

    @Query("""
            select coalesce(sum(allocation.allocatedAmount), 0)
            from ReceiptAllocation allocation
            join allocation.receipt receipt
            where receipt.deletedFlag = false
              and receipt.status = :status
              and allocation.sourceStatementId = :statementId
            """)
    BigDecimal sumAllocatedAmountBySourceStatementIdAndReceiptStatus(@Param("statementId") Long statementId,
                                                                     @Param("status") String status);

    @Query("""
            select coalesce(sum(allocation.allocatedAmount), 0)
            from ReceiptAllocation allocation
            join allocation.receipt receipt
            where receipt.deletedFlag = false
              and receipt.status = :status
              and allocation.sourceStatementId = :statementId
              and (:currentReceiptId is null or receipt.id <> :currentReceiptId)
            """)
    BigDecimal sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(@Param("statementId") Long statementId,
                                                                                        @Param("status") String status,
                                                                                        @Param("currentReceiptId") Long currentReceiptId);
}
