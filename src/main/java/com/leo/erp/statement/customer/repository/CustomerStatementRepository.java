package com.leo.erp.statement.customer.repository;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerStatementRepository extends JpaRepository<CustomerStatement, Long>,
        JpaSpecificationExecutor<CustomerStatement>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return ModuleKeys.CUSTOMER_STATEMENT;
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
    @Query("select statement from CustomerStatement statement "
            + "where statement.id = :id and statement.deletedFlag = false")
    Optional<CustomerStatement> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerStatement> findByIdAndDeletedFlagFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select statement from CustomerStatement statement "
            + "where statement.id = :id and statement.deletedFlag = false")
    Optional<CustomerStatement> findByIdAndDeletedFlagFalseForSettlementUpdate(@Param("id") Long id);

    @Query("""
            select distinct item.sourceSalesOrderItemId
            from CustomerStatement statement
            join statement.items item
            where statement.deletedFlag = false
              and statement.direction = '蓝字'
              and item.sourceSalesOrderItemId is not null
              and (:currentStatementId is null or statement.id <> :currentStatementId)
            """)
    List<Long> findOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(
            @Param("currentStatementId") Long currentStatementId
    );

    @Query("""
            select distinct item.sourceSalesOrderItemId
            from CustomerStatement statement
            join statement.items item
            where statement.deletedFlag = false
              and statement.direction = '蓝字'
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
              and (:currentStatementId is null or statement.id <> :currentStatementId)
            """)
    List<Long> findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds,
            @Param("currentStatementId") Long currentStatementId
    );

    @Query("""
            select distinct statement.id
            from CustomerStatement statement
            join statement.items item
            where statement.deletedFlag = false
              and statement.direction = '蓝字'
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            """)
    List<Long> findActiveStatementIdsBySourceSalesOrderItemIds(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds
    );

    @Query("""
            select statement
            from CustomerStatement statement
            where statement.sourceSalesReturnId = :sourceSalesReturnId
              and statement.direction = '红字'
              and statement.deletedFlag = false
            """)
    List<CustomerStatement> findBySourceSalesReturnIdAndDeletedFlagFalse(
            @Param("sourceSalesReturnId") Long sourceSalesReturnId);

    /**
     * 收款核销可分配候选：仅未删除的蓝字对账单。
     * 红字为退货冲销，不参与收款核销。
     */
    @Query("""
            select statement
            from CustomerStatement statement
            where statement.id = :id
              and statement.deletedFlag = false
              and statement.direction = '蓝字'
            """)
    Optional<CustomerStatement> findActiveBlueById(@Param("id") Long id);
}
