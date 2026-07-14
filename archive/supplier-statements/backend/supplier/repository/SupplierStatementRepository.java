package com.leo.erp.statement.supplier.repository;

import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupplierStatementRepository extends JpaRepository<SupplierStatement, Long>, JpaSpecificationExecutor<SupplierStatement> {

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<SupplierStatement> findByIdAndDeletedFlagFalse(Long id);

    @Query("""
            select distinct sourceItem.purchaseInbound.id
            from PurchaseInboundItem sourceItem
            where sourceItem.id in (
                select item.sourceInboundItemId
                from SupplierStatement ss
                join ss.items item
                where ss.deletedFlag = false
                  and item.sourceInboundItemId is not null
                  and (:currentStatementId is null or ss.id <> :currentStatementId)
            )
            """)
    List<Long> findOccupiedSourceInboundIdsExcludingCurrentStatement(
            @Param("currentStatementId") Long currentStatementId
    );

    @Query("""
            select distinct sourceItem.purchaseInbound.id
            from PurchaseInboundItem sourceItem
            where sourceItem.purchaseInbound.id in :sourceInboundIds
              and sourceItem.id in (
                  select item.sourceInboundItemId
                  from SupplierStatement ss
                  join ss.items item
                  where ss.deletedFlag = false
                    and item.sourceInboundItemId is not null
                    and (:currentStatementId is null or ss.id <> :currentStatementId)
              )
            """)
    List<Long> findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(
            @Param("sourceInboundIds") Collection<Long> sourceInboundIds,
            @Param("currentStatementId") Long currentStatementId
    );
}
