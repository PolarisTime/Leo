package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseInboundRepository extends JpaRepository<PurchaseInbound, Long>, JpaSpecificationExecutor<PurchaseInbound> {

    boolean existsByInboundNoAndDeletedFlagFalse(String inboundNo);

    @EntityGraph(attributePaths = "items")
    List<PurchaseInbound> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseInbound> findByIdAndDeletedFlagFalse(Long id);

    @EntityGraph(attributePaths = "items")
    List<PurchaseInbound> findAllByImportBatchIdAndDeletedFlagFalseOrderById(Long importBatchId);

    @EntityGraph(attributePaths = "items")
    @Query("""
            select distinct inbound
            from PurchaseInbound inbound
            join inbound.items item
            where inbound.deletedFlag = false
              and item.sourcePurchaseOrderItemId in :sourcePurchaseOrderItemIds
            """)
    List<PurchaseInbound> findAllActiveBySourcePurchaseOrderItemIds(
            @Param("sourcePurchaseOrderItemIds") Collection<Long> sourcePurchaseOrderItemIds
    );
}
