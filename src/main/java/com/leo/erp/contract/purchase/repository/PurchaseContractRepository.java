package com.leo.erp.contract.purchase.repository;

import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PurchaseContractRepository extends JpaRepository<PurchaseContract, Long>, JpaSpecificationExecutor<PurchaseContract> {

    boolean existsByContractNoAndDeletedFlagFalse(String contractNo);

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseContract> findByIdAndDeletedFlagFalse(Long id);
}
