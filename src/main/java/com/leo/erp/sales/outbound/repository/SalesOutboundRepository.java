package com.leo.erp.sales.outbound.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
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

public interface SalesOutboundRepository extends JpaRepository<SalesOutbound, Long>, JpaSpecificationExecutor<SalesOutbound>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return "sales-outbound";
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
    @Query("select outbound from SalesOutbound outbound where outbound.id = :id and outbound.deletedFlag = false")
    Optional<SalesOutbound> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByOutboundNoAndDeletedFlagFalse(String outboundNo);

    List<SalesOutbound> findByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<SalesOutbound> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select count(distinct outbound.id)
            from SalesOutbound outbound
            join outbound.items item
            where outbound.deletedFlag = false
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
              and (:excludedOutboundId is null or outbound.id <> :excludedOutboundId)
            """)
    long countActiveBySourceSalesOrderItemIdsExcludingOutbound(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds,
            @Param("excludedOutboundId") Long excludedOutboundId
    );

    @Query("""
            select distinct outbound
            from SalesOutbound outbound
            join fetch outbound.items item
            where outbound.deletedFlag = false
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
              and (:currentOutboundId is null or outbound.id <> :currentOutboundId)
            """)
    List<SalesOutbound> findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds,
            @Param("currentOutboundId") Long currentOutboundId
    );

    @Query("""
            select distinct outbound
            from SalesOutbound outbound
            join fetch outbound.items item
            where outbound.deletedFlag = false
              and outbound.status in :statuses
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            """)
    List<SalesOutbound> findAllByStatusesAndSourceSalesOrderItemIds(
            @Param("statuses") Collection<String> statuses,
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds
    );

    @EntityGraph(attributePaths = "items")
    @Query("""
            select distinct outbound
            from SalesOutbound outbound
            where outbound.deletedFlag = false
              and outbound.status = :status
              and exists (
                    select 1
                    from SalesOutboundItem item
                    where item.salesOutbound = outbound
                      and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
              )
            """)
    List<SalesOutbound> findAllWithItemsByStatusAndSourceSalesOrderItemIds(
            @Param("status") String status,
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds
    );

    interface SourceOutboundStatusProjection {
        Long getItemId();

        String getStatus();
    }

}
