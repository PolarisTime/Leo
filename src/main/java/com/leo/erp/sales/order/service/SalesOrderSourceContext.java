package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

record SalesOrderSourceContext(
        List<Long> sourceInboundItemIds,
        Map<Long, SourceInboundItemRecord> sourceInboundItemMap,
        Map<Long, SalesOrderSourceAllocation> inboundAllocatedMap,
        Map<Long, SalesOrderSourceAllocation> requestInboundAllocatedMap,
        LinkedHashSet<String> sourceInboundNos,
        LinkedHashSet<String> sourcePurchaseOrderNos
) {
    String resolvePurchaseInboundNo(String fallback) {
        return sourceInboundNos.isEmpty() ? fallback : String.join(", ", sourceInboundNos);
    }

    String resolvePurchaseOrderNo(String fallback) {
        return sourcePurchaseOrderNos.isEmpty() ? fallback : String.join(", ", sourcePurchaseOrderNos);
    }
}
