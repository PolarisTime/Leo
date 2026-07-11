package com.leo.erp.finance.payment.repository;

import com.leo.erp.finance.payment.domain.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    boolean existsByPaymentNoAndDeletedFlagFalse(String paymentNo);

    boolean existsByPaymentPurposeAndSourcePurchaseOrderIdAndDeletedFlagFalse(
            String paymentPurpose,
            Long sourcePurchaseOrderId
    );

    @EntityGraph(attributePaths = "items")
    Optional<Payment> findByIdAndDeletedFlagFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id and payment.deletedFlag = false")
    Optional<Payment> findByIdAndDeletedFlagFalseForUpdate(@Param("id") Long id);

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

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from Payment payment
            where payment.deletedFlag = false
              and payment.paymentPurpose = 'PURCHASE_PREPAYMENT'
              and payment.sourcePurchaseOrderId = :sourcePurchaseOrderId
              and payment.status = '已付款'
              and (:currentPaymentId is null or payment.id <> :currentPaymentId)
            """)
    BigDecimal sumPaidPurchasePrepaymentAmountExcludingId(
            @Param("sourcePurchaseOrderId") Long sourcePurchaseOrderId,
            @Param("currentPaymentId") Long currentPaymentId
    );
}
