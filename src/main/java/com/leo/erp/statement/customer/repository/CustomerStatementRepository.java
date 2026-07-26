package com.leo.erp.statement.customer.repository;

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
        return "customer-statement";
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
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            """)
    List<Long> findActiveStatementIdsBySourceSalesOrderItemIds(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds
    );
}
