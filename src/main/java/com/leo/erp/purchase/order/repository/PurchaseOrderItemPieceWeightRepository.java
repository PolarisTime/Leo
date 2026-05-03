package com.leo.erp.purchase.order.repository;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItemPieceWeight;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseOrderItemPieceWeightRepository extends JpaRepository<PurchaseOrderItemPieceWeight, Long> {

    List<PurchaseOrderItemPieceWeight> findByPurchaseOrderItemIdOrderByPieceNoAsc(Long purchaseOrderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select piece
            from PurchaseOrderItemPieceWeight piece
            where piece.purchaseOrderItemId = :purchaseOrderItemId
              and piece.salesOrderItemId is null
            order by piece.pieceNo asc
            """)
    List<PurchaseOrderItemPieceWeight> findAvailableByPurchaseOrderItemIdForUpdate(
            @Param("purchaseOrderItemId") Long purchaseOrderItemId
    );

    @Query("""
            select piece.purchaseOrderItemId as purchaseOrderItemId,
                   coalesce(sum(piece.weightTon), 0) as totalWeightTon
            from PurchaseOrderItemPieceWeight piece
            where piece.purchaseOrderItemId in :purchaseOrderItemIds
              and piece.salesOrderItemId is null
            group by piece.purchaseOrderItemId
            """)
    List<RemainingWeightSummary> summarizeRemainingWeightByPurchaseOrderItemIds(
            @Param("purchaseOrderItemIds") Collection<Long> purchaseOrderItemIds
    );

    @Modifying
    @Query("""
            delete from PurchaseOrderItemPieceWeight piece
            where piece.purchaseOrderItemId in :purchaseOrderItemIds
              and piece.salesOrderItemId is null
            """)
    void deleteUnallocatedByPurchaseOrderItemIdIn(@Param("purchaseOrderItemIds") Collection<Long> purchaseOrderItemIds);

    @Modifying
    @Query("""
            update PurchaseOrderItemPieceWeight piece
            set piece.salesOrderItemId = null
            where piece.salesOrderItemId in :salesOrderItemIds
            """)
    void releaseBySalesOrderItemIdIn(@Param("salesOrderItemIds") Collection<Long> salesOrderItemIds);

    interface RemainingWeightSummary {

        Long getPurchaseOrderItemId();

        java.math.BigDecimal getTotalWeightTon();
    }
}
