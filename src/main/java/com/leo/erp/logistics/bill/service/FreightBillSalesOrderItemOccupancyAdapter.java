package com.leo.erp.logistics.bill.service;

import com.leo.erp.logistics.bill.repository.FreightBillSourceItemRepository;
import com.leo.erp.sales.api.SalesOrderItemOccupancyQuery;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物流单行级占用适配销售模块的行级占用查询端口。
 */
@Component
public class FreightBillSalesOrderItemOccupancyAdapter implements SalesOrderItemOccupancyQuery {

    private final FreightBillSourceItemRepository sourceItemRepository;

    public FreightBillSalesOrderItemOccupancyAdapter(FreightBillSourceItemRepository sourceItemRepository) {
        this.sourceItemRepository = sourceItemRepository;
    }

    @Override
    public Map<Long, Integer> occupiedQuantities(Collection<Long> sourceSalesOrderItemIds) {
        if (sourceSalesOrderItemIds == null || sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> occupied = new LinkedHashMap<>();
        for (FreightBillSourceItemRepository.SourceItemOccupancySummary summary
                : sourceItemRepository.summarizeOccupiedQuantities(sourceSalesOrderItemIds, null)) {
            occupied.put(summary.getSourceSalesOrderItemId(), Math.toIntExact(summary.getTotalQuantity()));
        }
        return occupied;
    }
}
