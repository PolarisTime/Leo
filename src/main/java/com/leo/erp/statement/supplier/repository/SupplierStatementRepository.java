package com.leo.erp.statement.supplier.repository;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupplierStatementRepository extends JpaRepository<SupplierStatement, Long>, JpaSpecificationExecutor<SupplierStatement> {

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<SupplierStatement> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select distinct ss
            from SupplierStatement ss
            join fetch ss.items item
            where ss.deletedFlag = false
              and item.sourceNo in :sourceNos
              and (:currentStatementId is null or ss.id <> :currentStatementId)
            """)
    List<SupplierStatement> findAllBySourceNosExcludingCurrentStatement(
            @Param("sourceNos") Collection<String> sourceNos,
            @Param("currentStatementId") Long currentStatementId
    );
}
