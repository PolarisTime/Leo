package com.leo.erp.sales.returns.repository;

import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {

    /**
     * 销售退货单软删除时解除本单明细对上游明细（销售订单明细 / 销售出库明细）的引用
     * （与 V203/V204 同口径）。
     *
     * <p>删除是软删除（只置 {@code deleted_flag}，明细行保留），而明细上的两个来源列通过
     * 外键物理锁定上游明细；若不解绑，「已删除的退货单」会继续阻止销售订单/销售出库改单。</p>
     *
     * @return 受影响行数，仅用于日志；0 行不是错误
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update so_sales_return_item
               set source_sales_order_item_id = null,
                   source_sales_outbound_item_id = null
             where return_id = :returnId
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedReturn(@Param("returnId") Long returnId);

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
