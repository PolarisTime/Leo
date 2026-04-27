package com.leo.erp.statement.customer.repository;

import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CustomerStatementRepository extends JpaRepository<CustomerStatement, Long>, JpaSpecificationExecutor<CustomerStatement> {

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerStatement> findByIdAndDeletedFlagFalse(Long id);
}
