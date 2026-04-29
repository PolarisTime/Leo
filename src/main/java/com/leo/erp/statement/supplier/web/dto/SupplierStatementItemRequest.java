package com.leo.erp.statement.supplier.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SupplierStatementItemRequest(
        Long id,
        @NotBlank String sourceNo,
        @NotBlank String materialCode,
        @NotBlank String brand,
        @NotBlank String category,
        @NotBlank String material,
        @NotBlank String spec,
        String length,
        @NotBlank String unit,
        String batchNo,
        @NotNull @Min(0) Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000") BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal weighWeightTon,
        BigDecimal weightAdjustmentTon,
        BigDecimal weightAdjustmentAmount,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        BigDecimal amount
) {
    public SupplierStatementItemRequest(Long id,
                                        String sourceNo,
                                        String materialCode,
                                        String brand,
                                        String category,
                                        String material,
                                        String spec,
                                        String length,
                                        String unit,
                                        String batchNo,
                                        Integer quantity,
                                        String quantityUnit,
                                        BigDecimal pieceWeightTon,
                                        Integer piecesPerBundle,
                                        BigDecimal weightTon,
                                        BigDecimal unitPrice,
                                        BigDecimal amount) {
        this(id, sourceNo, materialCode, brand, category, material, spec, length, unit, batchNo, quantity,
                quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, null, null, null, unitPrice, amount);
    }

    public SupplierStatementItemRequest(String sourceNo,
                                        String materialCode,
                                        String brand,
                                        String category,
                                        String material,
                                        String spec,
                                        String length,
                                        String unit,
                                        String batchNo,
                                        Integer quantity,
                                        String quantityUnit,
                                        BigDecimal pieceWeightTon,
                                        Integer piecesPerBundle,
                                        BigDecimal weightTon,
                                        BigDecimal unitPrice,
                                        BigDecimal amount) {
        this(null, sourceNo, materialCode, brand, category, material, spec, length, unit, batchNo, quantity,
                quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, null, null, null, unitPrice, amount);
    }
}
