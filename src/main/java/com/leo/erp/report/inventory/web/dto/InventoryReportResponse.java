package com.leo.erp.report.inventory.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record InventoryReportResponse(
        Long id,
        Long materialId,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        String warehouseName,
        String batchNo,
        Integer onHandQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        String quantityUnit,
        BigDecimal onHandWeightTon,
        BigDecimal reservedWeightTon,
        BigDecimal availableWeightTon,
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
            Integer onHandQuantity,
            Integer reservedQuantity,
            Integer availableQuantity,
            String quantityUnit,
            BigDecimal onHandWeightTon,
            BigDecimal reservedWeightTon,
            BigDecimal availableWeightTon,
            String unit,
            BigDecimal pieceWeightTon,
            List<InventoryReportItemResponse> items
    ) {
        this(
                id,
                id,
                materialCode,
                brand,
                material,
                category,
                spec,
                length,
                warehouseName,
                batchNo,
                onHandQuantity,
                reservedQuantity,
                availableQuantity,
                quantityUnit,
                onHandWeightTon,
                reservedWeightTon,
                availableWeightTon,
                unit,
                pieceWeightTon,
                items
        );
    }

    @JsonProperty("quantity")
    @Schema(description = "现存数量，等同于 onHandQuantity", deprecated = true)
    public Integer quantity() {
        return onHandQuantity;
    }

    @JsonProperty("weightTon")
    @Schema(description = "现存重量，等同于 onHandWeightTon", deprecated = true)
    public BigDecimal weightTon() {
        return onHandWeightTon;
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
            BigDecimal pieceWeightTon,
            List<InventoryReportItemResponse> items
    ) {
        this(
                id,
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
                0,
                quantity,
                quantityUnit,
                weightTon,
                BigDecimal.ZERO,
                weightTon,
                unit,
                pieceWeightTon,
                items
        );
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
                0,
                quantity,
                quantityUnit,
                weightTon,
                BigDecimal.ZERO,
                weightTon,
                unit,
                pieceWeightTon,
                List.of()
        );
    }
}
