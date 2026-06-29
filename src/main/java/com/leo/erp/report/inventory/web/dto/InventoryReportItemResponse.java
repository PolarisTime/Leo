package com.leo.erp.report.inventory.web.dto;

import java.math.BigDecimal;

public record InventoryReportItemResponse(
        String id,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        String warehouseName,
        String batchNo,
        String outboundNo,
        String outboundDate,
        Integer quantity,
        String quantityUnit,
        BigDecimal weightTon,
        String unit,
        BigDecimal pieceWeightTon
) {
}
