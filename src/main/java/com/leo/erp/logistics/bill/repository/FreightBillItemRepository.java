package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 物流明细仓储：物流明细既承载行级来源信息，也是行级占用的唯一数量真源。
 */
public interface FreightBillItemRepository extends JpaRepository<FreightBillItem, Long> {

    /**
     * 物流单软删除时解除本单明细对来源销售订单明细的引用（与 V203/V204 同口径）。
     *
     * <p>删除是软删除（只置 {@code deleted_flag}，明细行保留），而明细上的
     * {@code source_sales_order_item_id} 通过外键物理锁定上游订单明细；若不解绑，
     * 「已删除的物流单」会继续阻止销售订单改单。只解绑本单（按 {@code bill_id}）。</p>
     *
     * @return 受影响行数，仅用于日志；0 行不是错误
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update lg_freight_bill_item
               set source_sales_order_item_id = null
             where bill_id = :billId
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedBill(@Param("billId") Long billId);


    /**
     * 按来源销售订单明细汇总已占用数量，排除当前物流单自身占用。
     * <p>物流明细无独立删除标志：行被移除即释放，父单软删由 join 过滤。
     */
    @Query("""
            select item.sourceSalesOrderItemId as sourceSalesOrderItemId,
                   sum(item.quantity) as totalQuantity
            from FreightBillItem item
            join item.freightBill bill
            where bill.deletedFlag = false
              and item.sourceSalesOrderItemId in :sourceItemIds
              and (:currentBillId is null or bill.id <> :currentBillId)
            group by item.sourceSalesOrderItemId
            """)
    List<FreightBillItemOccupancySummary> summarizeOccupiedQuantities(
            @Param("sourceItemIds") Collection<Long> sourceItemIds,
            @Param("currentBillId") Long currentBillId
    );

    interface FreightBillItemOccupancySummary {

        Long getSourceSalesOrderItemId();

        Long getTotalQuantity();
    }
}
