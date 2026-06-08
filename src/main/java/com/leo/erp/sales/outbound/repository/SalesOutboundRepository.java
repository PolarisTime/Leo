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
    List<SalesOutbound> findAllByDeletedFlagFalse();

    @EntityGraph(attributePaths = "items")
    Optional<SalesOutbound> findByIdAndDeletedFlagFalse(Long id);

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
}
