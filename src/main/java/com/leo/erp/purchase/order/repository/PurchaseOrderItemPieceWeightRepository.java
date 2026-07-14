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

    @Query(value = """
            SELECT piece.purchase_order_item_id AS purchase_order_item_id,
                   COALESCE(SUM(piece.weight_ton), 0) AS total_weight_ton
              FROM po_purchase_order_item_piece_weight piece
              JOIN so_sales_order_item sales_item ON sales_item.id = piece.sales_order_item_id
              JOIN so_sales_order sales_order ON sales_order.id = sales_item.order_id
             WHERE piece.purchase_order_item_id IN (:purchaseOrderItemIds)
               AND piece.sales_order_item_id IS NOT NULL
               AND (
                    sales_order.status = :salesCompletedStatus
                    OR EXISTS (
                        SELECT 1
                          FROM so_sales_outbound_item outbound_item
                          JOIN so_sales_outbound outbound ON outbound.id = outbound_item.outbound_id
                         WHERE outbound.deleted_flag = FALSE
                           AND outbound.status = :auditedStatus
                           AND outbound_item.source_sales_order_item_id = sales_item.id
                    )
                    OR EXISTS (
                        SELECT 1
                          FROM st_customer_statement_item statement_item
                          JOIN st_customer_statement statement ON statement.id = statement_item.statement_id
                         WHERE statement.deleted_flag = FALSE
                           AND statement_item.source_sales_order_item_id = sales_item.id
                    )
                    OR EXISTS (
                        SELECT 1
                          FROM fm_receipt_allocation allocation
                          JOIN fm_receipt receipt ON receipt.id = allocation.receipt_id
                          JOIN st_customer_statement statement ON statement.id = allocation.source_statement_id
                          JOIN st_customer_statement_item statement_item ON statement_item.statement_id = statement.id
                         WHERE receipt.deleted_flag = FALSE
                           AND receipt.status = :receivedStatus
                           AND statement.deleted_flag = FALSE
                           AND statement_item.source_sales_order_item_id = sales_item.id
                    )
               )
             GROUP BY piece.purchase_order_item_id
            """, nativeQuery = true)
    List<PurchaseOrderItemWeightSummary> summarizeLockedSalesWeightByPurchaseOrderItemIds(
            @Param("purchaseOrderItemIds") Collection<Long> purchaseOrderItemIds,
            @Param("salesCompletedStatus") String salesCompletedStatus,
            @Param("auditedStatus") String auditedStatus,
            @Param("receivedStatus") String receivedStatus
    );

    @Query("""
            select piece.salesOrderItemId as salesOrderItemId,
                   coalesce(sum(piece.weightTon), 0) as totalWeightTon
            from PurchaseOrderItemPieceWeight piece
            where piece.salesOrderItemId in :salesOrderItemIds
            group by piece.salesOrderItemId
            """)
    List<SalesOrderItemWeightSummary> summarizeBySalesOrderItemIds(
            @Param("salesOrderItemIds") Collection<Long> salesOrderItemIds
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

    interface SalesOrderItemWeightSummary {

        Long getSalesOrderItemId();

        java.math.BigDecimal getTotalWeightTon();
    }

    interface PurchaseOrderItemWeightSummary {

        Long getPurchaseOrderItemId();

        java.math.BigDecimal getTotalWeightTon();
    }
}
