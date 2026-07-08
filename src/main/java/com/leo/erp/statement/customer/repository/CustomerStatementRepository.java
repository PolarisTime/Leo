package com.leo.erp.statement.customer.repository;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerStatementRepository extends JpaRepository<CustomerStatement, Long>, JpaSpecificationExecutor<CustomerStatement> {

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerStatement> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select distinct cs
            from CustomerStatement cs
            join fetch cs.items item
            where cs.deletedFlag = false
              and item.sourceNo in :sourceNos
              and (:currentStatementId is null or cs.id <> :currentStatementId)
            """)
    List<CustomerStatement> findAllBySourceNosExcludingCurrentStatement(
            @Param("sourceNos") Collection<String> sourceNos,
            @Param("currentStatementId") Long currentStatementId
    );

    @Query("""
            select distinct item.sourceSalesOrderItemId
            from CustomerStatement statement
            join statement.items item
            where statement.deletedFlag = false
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            """)
    List<Long> findSourceSalesOrderItemIds(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds
    );
}
