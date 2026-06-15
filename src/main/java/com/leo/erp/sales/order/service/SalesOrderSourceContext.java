package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

record SalesOrderSourceContext(
        List<Long> sourceInboundItemIds,
        List<Long> sourcePurchaseOrderItemIds,
        Map<Long, SourceInboundItemRecord> sourceInboundItemMap,
        Map<Long, SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap,
        Map<Long, SalesOrderSourceAllocation> inboundAllocatedMap,
        Map<Long, SalesOrderSourceAllocation> purchaseOrderAllocatedMap,
        Map<Long, BigDecimal> purchaseOrderRemainingWeightMap,
        Map<Long, SalesOrderSourceAllocation> requestInboundAllocatedMap,
        Map<Long, SalesOrderSourceAllocation> requestPurchaseOrderAllocatedMap,
        LinkedHashSet<String> sourceInboundNos,
        LinkedHashSet<String> sourcePurchaseOrderNos
) {
    SalesOrderSourceContext withPurchaseOrderRemainingWeightMap(Map<Long, BigDecimal> remainingWeightMap) {
        return new SalesOrderSourceContext(
                sourceInboundItemIds,
                sourcePurchaseOrderItemIds,
                sourceInboundItemMap,
                sourcePurchaseOrderItemMap,
                inboundAllocatedMap,
                purchaseOrderAllocatedMap,
                remainingWeightMap,
                requestInboundAllocatedMap,
                requestPurchaseOrderAllocatedMap,
                sourceInboundNos,
                sourcePurchaseOrderNos
        );
    }

    String resolvePurchaseInboundNo(String fallback) {
        return sourceInboundNos.isEmpty() ? fallback : String.join(", ", sourceInboundNos);
    }

    String resolvePurchaseOrderNo(String fallback) {
        return sourcePurchaseOrderNos.isEmpty() ? fallback : String.join(", ", sourcePurchaseOrderNos);
    }
}
