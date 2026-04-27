package com.leo.erp.contract.sales.repository;

import com.leo.erp.contract.sales.domain.entity.SalesContract;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SalesContractRepository extends JpaRepository<SalesContract, Long>, JpaSpecificationExecutor<SalesContract> {

    boolean existsByContractNoAndDeletedFlagFalse(String contractNo);

    @EntityGraph(attributePaths = "items")
    Optional<SalesContract> findByIdAndDeletedFlagFalse(Long id);
}
