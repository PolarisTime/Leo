package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 物流明细仓储：物流明细既承载行级来源信息，也是行级占用的唯一数量真源。
 */
public interface FreightBillItemRepository extends JpaRepository<FreightBillItem, Long> {

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
