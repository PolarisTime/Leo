package com.leo.erp.inventory.repository;

import com.leo.erp.inventory.domain.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * 批量预加载指定来源明细的未删除事务幂等键 (sourceDocumentType, sourceItemId, transactionType)，
     * 供期初回填在内存中做差集判断，避免逐行 exists 查询。
     */
    @Query("""
            select transaction.sourceDocumentType as sourceDocumentType,
                   transaction.sourceItemId as sourceItemId,
                   transaction.transactionType as transactionType
            from InventoryTransaction transaction
            where transaction.deletedFlag = false
              and transaction.sourceItemId in :sourceItemIds
            """)
    List<ActiveSourceKey> findActiveSourceKeysBySourceItemIdIn(
            @Param("sourceItemIds") Collection<Long> sourceItemIds);

    /**
     * 未删除库存事务的幂等键投影。
     */
    interface ActiveSourceKey {
        String getSourceDocumentType();

        Long getSourceItemId();

        String getTransactionType();
    }
}
