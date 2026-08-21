package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseInboundRepository extends JpaRepository<PurchaseInbound, Long>,
        JpaSpecificationExecutor<PurchaseInbound>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "purchase-inbound";
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select inbound from PurchaseInbound inbound where inbound.id = :id and inbound.deletedFlag = false")
    Optional<PurchaseInbound> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByInboundNoAndDeletedFlagFalse(String inboundNo);

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseInbound> findByIdAndDeletedFlagFalse(Long id);

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
