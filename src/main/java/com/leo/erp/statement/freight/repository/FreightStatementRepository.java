package com.leo.erp.statement.freight.repository;

import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FreightStatementRepository extends JpaRepository<FreightStatement, Long>, JpaSpecificationExecutor<FreightStatement> {

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<FreightStatement> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select distinct fs
            from FreightStatement fs
            join fetch fs.items item
            where fs.deletedFlag = false
              and item.sourceNo in :sourceNos
              and (:currentStatementId is null or fs.id <> :currentStatementId)
            """)
    List<FreightStatement> findAllBySourceNosExcludingCurrentStatement(
            @Param("sourceNos") Collection<String> sourceNos,
            @Param("currentStatementId") Long currentStatementId
    );
}
