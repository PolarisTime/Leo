package com.leo.erp.finance.receipt.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return "receipt";
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select receipt from Receipt receipt where receipt.id = :id and receipt.deletedFlag = false")
    Optional<Receipt> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByReceiptNoAndDeletedFlagFalse(String receiptNo);

    @EntityGraph(attributePaths = "items")
    Optional<Receipt> findByIdAndDeletedFlagFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receipt from Receipt receipt where receipt.id = :id and receipt.deletedFlag = false")
    Optional<Receipt> findByIdAndDeletedFlagFalseForUpdate(@Param("id") Long id);

    @Query("""
            select coalesce(sum(receipt.amount), 0)
            from Receipt receipt
            where receipt.deletedFlag = false
              and receipt.sourceCustomerStatementId = :statementId
              and receipt.status = :status
            """)
    BigDecimal sumAmountBySourceStatementIdAndStatus(@Param("statementId") Long statementId, @Param("status") String status);

    @Query("""
            select coalesce(sum(receipt.amount), 0)
            from Receipt receipt
            where receipt.deletedFlag = false
              and receipt.sourceCustomerStatementId = :statementId
              and receipt.status = :status
              and (:currentReceiptId is null or receipt.id <> :currentReceiptId)
            """)
    BigDecimal sumAmountBySourceStatementIdAndStatusExcludingId(@Param("statementId") Long statementId,
                                                                @Param("status") String status,
                                                                @Param("currentReceiptId") Long currentReceiptId);
}
