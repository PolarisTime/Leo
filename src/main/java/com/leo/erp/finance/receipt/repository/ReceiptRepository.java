package com.leo.erp.finance.receipt.repository;

import com.leo.erp.common.support.ModuleKeys;
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
        return ModuleKeys.RECEIPT;
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

    /**
     * 详情查询专用覆写: 收款单详情允许查看已删除记录, 因此不能改为
     * {@link #findByIdAndDeletedFlagFalse(Long)}(会额外过滤 deleted_flag, 属可见性语义变更)。
     * 这里只给 {@code items} 挂抓取图, 让详情装配由 1 + N 条 select 收敛为 1 条。
     */
    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Receipt> findById(Long id);

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
