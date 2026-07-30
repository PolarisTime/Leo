package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseOrderPickupListResponse(
        int orderCount,
        int supplierCount,
        int itemCount,
        int totalQuantity,
        BigDecimal totalWeightTon,
        List<Group> groups,
        List<String> warnings
) {

    public record Group(
            String key,
            Long supplierId,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            int orderCount,
            int itemCount,
            int totalQuantity,
            BigDecimal totalWeightTon,
            List<Item> items
    ) {
    }

    public record Item(
            Long itemId,
            Long orderId,
            String orderNo,
            Integer lineNo,
            Long warehouseId,
            String warehouseName,
            String brand,
            String category,
            String material,
            String spec,
            String length,
            Integer pickupQuantity,
            BigDecimal pieceWeightTon,
            BigDecimal pickupWeightTon
    ) {
    }
}
