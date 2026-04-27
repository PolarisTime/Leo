package com.leo.erp.finance.payment.repository;

import com.leo.erp.finance.payment.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    boolean existsByPaymentNoAndDeletedFlagFalse(String paymentNo);

    Optional<Payment> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from Payment payment
            where payment.deletedFlag = false
              and payment.sourceStatementId = :statementId
              and payment.status = :status
            """)
    BigDecimal sumAmountBySourceStatementIdAndStatus(@Param("statementId") Long statementId, @Param("status") String status);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from Payment payment
            where payment.deletedFlag = false
              and payment.sourceStatementId = :statementId
              and payment.status = :status
              and (:currentPaymentId is null or payment.id <> :currentPaymentId)
            """)
    BigDecimal sumAmountBySourceStatementIdAndStatusExcludingId(@Param("statementId") Long statementId,
                                                                @Param("status") String status,
                                                                @Param("currentPaymentId") Long currentPaymentId);
}
