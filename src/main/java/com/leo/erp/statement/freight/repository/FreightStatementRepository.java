package com.leo.erp.statement.freight.repository;

import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FreightStatementRepository extends JpaRepository<FreightStatement, Long>, JpaSpecificationExecutor<FreightStatement> {

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<FreightStatement> findByIdAndDeletedFlagFalse(Long id);
}
