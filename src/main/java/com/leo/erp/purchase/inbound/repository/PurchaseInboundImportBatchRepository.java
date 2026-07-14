package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseInboundImportBatchRepository
        extends JpaRepository<PurchaseInboundImportBatch, Long> {

    Optional<PurchaseInboundImportBatch> findByIdAndDeletedFlagFalse(Long id);
}
