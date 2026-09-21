package com.leo.erp.logistics.bill.repository;

import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FreightBillSourceItemRepository extends JpaRepository<FreightBillSourceItem, Long> {

    /**
     * 按来源销售订单明细汇总已占用数量，排除当前物流单自身占用。
     */
    @Query("""
            select relation.sourceSalesOrderItemId as sourceSalesOrderItemId,
                   sum(relation.quantity) as totalQuantity
            from FreightBillSourceItem relation
            where relation.activeFlag = true
              and relation.sourceSalesOrderItemId in :sourceItemIds
              and (:currentBillId is null or relation.freightBill.id <> :currentBillId)
            group by relation.sourceSalesOrderItemId
            """)
    List<SourceItemOccupancySummary> summarizeOccupiedQuantities(@Param("sourceItemIds") Collection<Long> sourceItemIds,
                                                                 @Param("currentBillId") Long currentBillId);

    @Query("""
            select relation
            from FreightBillSourceItem relation
            where relation.activeFlag = true
              and relation.freightBill.id = :billId
            """)
    List<FreightBillSourceItem> findActiveByBillId(@Param("billId") Long billId);

    interface SourceItemOccupancySummary {

        Long getSourceSalesOrderItemId();

        Long getTotalQuantity();
    }
}
