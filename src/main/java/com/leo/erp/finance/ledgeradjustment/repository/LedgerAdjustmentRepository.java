package com.leo.erp.finance.ledgeradjustment.repository;

import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface LedgerAdjustmentRepository extends JpaRepository<LedgerAdjustment, Long>, JpaSpecificationExecutor<LedgerAdjustment> {

    boolean existsByAdjustmentNoAndDeletedFlagFalse(String adjustmentNo);

    Optional<LedgerAdjustment> findByIdAndDeletedFlagFalse(Long id);
}
