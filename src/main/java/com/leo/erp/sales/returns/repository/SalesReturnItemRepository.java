package com.leo.erp.sales.returns.repository;

import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {

    @Query("""
            select item
            from SalesReturnItem item
            join fetch item.salesReturn ret
            where ret.deletedFlag = false
              and ret.id = :returnId
            """)
    List<SalesReturnItem> findActiveByReturnId(@Param("returnId") Long returnId);

    /**
     * 按来源销售出库明细汇总已审核退货数量，用于累计超退校验。
     * 排除当前退货单自身，避免保存后二次校验重复累计。
     */
    @Query("""
            select item.sourceSalesOutboundItemId as sourceSalesOutboundItemId,
                   coalesce(sum(item.quantity), 0) as totalQuantity
            from SalesReturnItem item
            join item.salesReturn ret
            where ret.deletedFlag = false
              and ret.status = :status
              and item.sourceSalesOutboundItemId in :sourceSalesOutboundItemIds
              and (:excludedReturnId is null or ret.id <> :excludedReturnId)
            group by item.sourceSalesOutboundItemId
            """)
    List<SourceOutboundReturnSummary> summarizeAuditedQuantityBySourceOutboundItemIds(
            @Param("sourceSalesOutboundItemIds") Collection<Long> sourceSalesOutboundItemIds,
            @Param("status") String status,
            @Param("excludedReturnId") Long excludedReturnId
    );

    interface SourceOutboundReturnSummary {

        Long getSourceSalesOutboundItemId();

        Long getTotalQuantity();
    }

    /**
     * 按来源销售订单明细汇总已审核退货数量/重量/金额，用于客户对账净额计算
     * （净额 = 已审核出库 − 已审核退货）。
     */
    @Query("""
            select item.sourceSalesOrderItemId as sourceSalesOrderItemId,
                   coalesce(sum(item.quantity), 0) as totalQuantity,
                   coalesce(sum(item.weightTon), 0) as totalWeightTon,
                   coalesce(sum(item.amount), 0) as totalAmount
            from SalesReturnItem item
            join item.salesReturn ret
            where ret.deletedFlag = false
              and ret.status = :status
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            group by item.sourceSalesOrderItemId
            """)
    List<SourceOrderReturnSummary> summarizeAuditedReturnBySourceSalesOrderItemIds(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds,
            @Param("status") String status
    );

    interface SourceOrderReturnSummary {

        Long getSourceSalesOrderItemId();

        Long getTotalQuantity();

        BigDecimal getTotalWeightTon();

        BigDecimal getTotalAmount();
    }
}
