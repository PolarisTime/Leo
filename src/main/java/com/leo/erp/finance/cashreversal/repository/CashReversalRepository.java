package com.leo.erp.finance.cashreversal.repository;

import com.leo.erp.finance.cashreversal.domain.entity.CashReversal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface CashReversalRepository extends JpaRepository<CashReversal, Long>,
        JpaSpecificationExecutor<CashReversal> {

    boolean existsByReversalNoAndDeletedFlagFalse(String reversalNo);

    Optional<CashReversal> findByIdAndDeletedFlagFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reversal from CashReversal reversal where reversal.id = :id and reversal.deletedFlag = false")
    Optional<CashReversal> findByIdAndDeletedFlagFalseForUpdate(@Param("id") Long id);

    @Query("""
            select coalesce(sum(reversal.amount), 0)
            from CashReversal reversal
            where reversal.deletedFlag = false
              and reversal.status = '已审核'
              and reversal.originalPaymentId = :originalPaymentId
              and (:currentReversalId is null or reversal.id <> :currentReversalId)
            """)
    BigDecimal sumAuditedAmountByOriginalPaymentIdExcludingId(
            @Param("originalPaymentId") Long originalPaymentId,
            @Param("currentReversalId") Long currentReversalId
    );

    @Query("""
            select coalesce(sum(reversal.amount), 0)
            from CashReversal reversal
            where reversal.deletedFlag = false
              and reversal.status = '已审核'
              and reversal.originalReceiptId = :originalReceiptId
              and (:currentReversalId is null or reversal.id <> :currentReversalId)
            """)
    BigDecimal sumAuditedAmountByOriginalReceiptIdExcludingId(
            @Param("originalReceiptId") Long originalReceiptId,
            @Param("currentReversalId") Long currentReversalId
    );
}
