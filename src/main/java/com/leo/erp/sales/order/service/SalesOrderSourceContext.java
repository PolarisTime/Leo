package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

record SalesOrderSourceContext(
        List<Long> sourceInboundItemIds,
        Map<Long, SourceInboundItemRecord> sourceInboundItemMap,
        Map<Long, SalesOrderSourceAllocation> inboundAllocatedMap,
        Map<Long, SalesOrderSourceAllocation> requestInboundAllocatedMap,
        Map<Long, SalesOrderItem> legacyPurchaseSourceItemMap,
        Map<Long, String> legacyPurchaseOrderNoByItemId,
        LinkedHashSet<String> sourceInboundNos,
        LinkedHashSet<String> sourcePurchaseOrderNos
) {
    String resolvePurchaseInboundNo() {
        return sourceInboundNos.isEmpty() ? null : String.join(", ", sourceInboundNos);
    }

    String resolvePurchaseOrderNo() {
        return sourcePurchaseOrderNos.isEmpty() ? null : String.join(", ", sourcePurchaseOrderNos);
    }
}
