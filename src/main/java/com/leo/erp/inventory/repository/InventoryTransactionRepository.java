package com.leo.erp.inventory.repository;

import com.leo.erp.inventory.domain.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long>,
        JpaSpecificationExecutor<InventoryTransaction> {

    boolean existsBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(
            String sourceDocumentType,
            Long sourceItemId,
            String transactionType
    );

    List<InventoryTransaction> findBySourceDocumentTypeAndSourceDocumentIdAndDeletedFlagFalse(
            String sourceDocumentType,
            Long sourceDocumentId
    );

    List<InventoryTransaction> findBySourceDocumentTypeAndSourceItemIdAndTransactionTypeAndDeletedFlagFalse(
            String sourceDocumentType,
            Long sourceItemId,
            String transactionType
    );
}
