package com.leo.erp.report.inventory.web.dto;

import java.math.BigDecimal;

public record InventoryReportItemResponse(
        Long id,
        Long materialId,
        Long warehouseId,
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
    public InventoryReportItemResponse(
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
        this(
                Long.valueOf(id),
                null,
                null,
                materialCode,
                brand,
                material,
                category,
                spec,
                length,
                warehouseName,
                batchNo,
                outboundNo,
                outboundDate,
                quantity,
                quantityUnit,
                weightTon,
                unit,
                pieceWeightTon
        );
    }
}
