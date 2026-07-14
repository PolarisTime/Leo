package com.leo.erp.sales.outbound.repository;

import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesOutboundRepository extends JpaRepository<SalesOutbound, Long>, JpaSpecificationExecutor<SalesOutbound> {

    boolean existsByOutboundNoAndDeletedFlagFalse(String outboundNo);

    List<SalesOutbound> findByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    List<SalesOutbound> findByOutboundNoInAndDeletedFlagFalse(Collection<String> outboundNos);

    @EntityGraph(attributePaths = "items")
    List<SalesOutbound> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<SalesOutbound> findByIdAndDeletedFlagFalse(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<SalesOutbound> findBySourceFreightBillIdAndDeletedFlagFalse(Long sourceFreightBillId);

    boolean existsBySourceFreightBillIdAndDeletedFlagFalse(Long sourceFreightBillId);

    @Query("""
            select outbound.sourceFreightBillId as freightBillId,
                   outbound.id as outboundId,
                   outbound.outboundNo as outboundNo
            from SalesOutbound outbound
            where outbound.deletedFlag = false
              and outbound.sourceFreightBillId in :freightBillIds
            """)
    List<FreightBillOutboundReference> findActiveFreightBillOutboundReferences(
            @Param("freightBillIds") Collection<Long> freightBillIds
    );

    @Query("""
            select count(outbound.id)
            from SalesOutbound outbound
            where outbound.deletedFlag = false
              and outbound.sourceFreightBillId = :freightBillId
              and outbound.id <> :excludedOutboundId
            """)
    long countActiveBySourceFreightBillIdExcludingOutbound(
            @Param("freightBillId") Long freightBillId,
            @Param("excludedOutboundId") Long excludedOutboundId
    );

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
              and outbound.status = :status
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            """)
    List<SalesOutbound> findAllByStatusAndSourceSalesOrderItemIds(
            @Param("status") String status,
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds
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

    @EntityGraph(attributePaths = "items")
    @Query("""
            select distinct outbound
            from SalesOutbound outbound
            where outbound.deletedFlag = false
              and exists (
                    select 1
                    from SalesOutboundItem item
                    where item.salesOutbound = outbound
                      and item.id in :itemIds
              )
            """)
    List<SalesOutbound> findAllWithItemsByItemIds(@Param("itemIds") Collection<Long> itemIds);

    @Query("""
            select distinct outbound.id
            from SalesOutbound outbound
            join outbound.items item
            where outbound.deletedFlag = false
              and item.id in :itemIds
            """)
    List<Long> findSourceOutboundIdsByItemIds(@Param("itemIds") Collection<Long> itemIds);

    @Query("""
            select distinct item.sourceSalesOrderItemId
            from SalesOutbound outbound
            join outbound.items item
            where outbound.deletedFlag = false
              and outbound.status = :status
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            """)
    List<Long> findSourceSalesOrderItemIdsByStatus(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds,
            @Param("status") String status
    );

    @Query("""
            select item.id as itemId,
                   outbound.status as status
            from SalesOutbound outbound
            join outbound.items item
            where outbound.deletedFlag = false
              and item.id in :itemIds
            """)
    List<SourceOutboundStatusProjection> findSourceOutboundStatusesByItemIds(
            @Param("itemIds") Collection<Long> itemIds
    );

    interface SourceOutboundStatusProjection {
        Long getItemId();

        String getStatus();
    }

    interface FreightBillOutboundReference {
        Long getFreightBillId();

        Long getOutboundId();

        String getOutboundNo();
    }
}
