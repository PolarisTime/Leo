package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FreightBillItemRequest(
        Long id,
        @NotBlank String sourceNo,
        @NotBlank String customerName,
        @NotBlank String projectName,
        @NotBlank String materialCode,
        String materialName,
        @NotBlank String brand,
        @NotBlank String category,
        @NotBlank String material,
        @NotBlank String spec,
        String length,
        @NotNull @Min(0) Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000") BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        String batchNo,
        @NotNull @DecimalMin("0.000") BigDecimal weightTon,
        String warehouseName
) {
    public FreightBillItemRequest(String sourceNo,
                                  String customerName,
                                  String projectName,
                                  String materialCode,
                                  String materialName,
                                  String brand,
                                  String category,
                                  String material,
                                  String spec,
                                  String length,
                                  Integer quantity,
                                  String quantityUnit,
                                  BigDecimal pieceWeightTon,
                                  Integer piecesPerBundle,
                                  String batchNo,
                                  BigDecimal weightTon,
                                  String warehouseName) {
        this(null, sourceNo, customerName, projectName, materialCode, materialName, brand, category, material, spec,
                length, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, batchNo, weightTon, warehouseName);
    }
}
