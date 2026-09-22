package com.leo.erp.logistics.bill.service;

import com.leo.erp.logistics.bill.repository.FreightBillItemRepository;
import com.leo.erp.sales.api.SalesOrderItemOccupancyQuery;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物流单行级占用适配销售模块的行级占用查询端口。
 * <p>数量真源为未删除物流单的 {@code lg_freight_bill_item.quantity} 聚合。
 */
@Component
public class FreightBillSalesOrderItemOccupancyAdapter implements SalesOrderItemOccupancyQuery {

    private final FreightBillItemRepository itemRepository;

    public FreightBillSalesOrderItemOccupancyAdapter(FreightBillItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public Map<Long, Integer> occupiedQuantities(Collection<Long> sourceSalesOrderItemIds) {
        if (sourceSalesOrderItemIds == null || sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> occupied = new LinkedHashMap<>();
        for (FreightBillItemRepository.FreightBillItemOccupancySummary summary
                : itemRepository.summarizeOccupiedQuantities(sourceSalesOrderItemIds, null)) {
            occupied.put(summary.getSourceSalesOrderItemId(), Math.toIntExact(summary.getTotalQuantity()));
        }
        return occupied;
    }
}
