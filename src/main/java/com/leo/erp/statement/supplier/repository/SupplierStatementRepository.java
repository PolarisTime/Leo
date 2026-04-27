package com.leo.erp.statement.supplier.repository;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SupplierStatementRepository extends JpaRepository<SupplierStatement, Long>, JpaSpecificationExecutor<SupplierStatement> {

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<SupplierStatement> findByIdAndDeletedFlagFalse(Long id);
}
