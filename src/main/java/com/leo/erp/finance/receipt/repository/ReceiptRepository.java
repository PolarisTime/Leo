package com.leo.erp.finance.receipt.repository;

import com.leo.erp.finance.receipt.domain.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt> {

    boolean existsByReceiptNoAndDeletedFlagFalse(String receiptNo);

    Optional<Receipt> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select coalesce(sum(receipt.amount), 0)
            from Receipt receipt
            where receipt.deletedFlag = false
              and receipt.sourceStatementId = :statementId
              and receipt.status = :status
            """)
    BigDecimal sumAmountBySourceStatementIdAndStatus(@Param("statementId") Long statementId, @Param("status") String status);

    @Query("""
            select coalesce(sum(receipt.amount), 0)
            from Receipt receipt
            where receipt.deletedFlag = false
              and receipt.sourceStatementId = :statementId
              and receipt.status = :status
              and (:currentReceiptId is null or receipt.id <> :currentReceiptId)
            """)
    BigDecimal sumAmountBySourceStatementIdAndStatusExcludingId(@Param("statementId") Long statementId,
                                                                @Param("status") String status,
                                                                @Param("currentReceiptId") Long currentReceiptId);
}
