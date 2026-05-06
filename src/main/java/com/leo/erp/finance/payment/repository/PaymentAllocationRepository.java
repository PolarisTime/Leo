package com.leo.erp.finance.payment.repository;

import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    @Query("""
            select coalesce(sum(allocation.allocatedAmount), 0)
            from PaymentAllocation allocation
            join allocation.payment payment
            where payment.deletedFlag = false
              and payment.businessType = :businessType
              and payment.status = :status
              and allocation.sourceStatementId = :statementId
            """)
    BigDecimal sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatus(@Param("statementId") Long statementId,
                                                                             @Param("businessType") String businessType,
                                                                             @Param("status") String status);

    @Query("""
            select coalesce(sum(allocation.allocatedAmount), 0)
            from PaymentAllocation allocation
            join allocation.payment payment
            where payment.deletedFlag = false
              and payment.businessType = :businessType
              and payment.status = :status
              and allocation.sourceStatementId = :statementId
              and (:currentPaymentId is null or payment.id <> :currentPaymentId)
            """)
    BigDecimal sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(@Param("statementId") Long statementId,
                                                                                                @Param("businessType") String businessType,
                                                                                                @Param("status") String status,
                                                                                                @Param("currentPaymentId") Long currentPaymentId);
}
