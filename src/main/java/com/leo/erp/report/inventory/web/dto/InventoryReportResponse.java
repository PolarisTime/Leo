package com.leo.erp.report.inventory.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record InventoryReportResponse(
        Long id,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        String warehouseName,
        String batchNo,
        Integer quantity,
        String quantityUnit,
        BigDecimal weightTon,
        String unit,
        BigDecimal pieceWeightTon,
        List<InventoryReportItemResponse> items
) {
    public InventoryReportResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public InventoryReportResponse(
            Long id,
            String materialCode,
            String brand,
            String material,
            String category,
            String spec,
            String length,
            String warehouseName,
            String batchNo,
            Integer quantity,
            String quantityUnit,
            BigDecimal weightTon,
            String unit,
            BigDecimal pieceWeightTon
    ) {
        this(
                id,
                materialCode,
                brand,
                material,
                category,
                spec,
                length,
                warehouseName,
                batchNo,
                quantity,
                quantityUnit,
                weightTon,
                unit,
                pieceWeightTon,
                List.of()
        );
    }
}
