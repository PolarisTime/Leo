package com.leo.erp.sales.order.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalesOrderItemRequest(
        Long id,
        @NotBlank String materialCode,
        @NotBlank String brand,
        @NotBlank String category,
        @NotBlank String material,
        @NotBlank String spec,
        String length,
        @NotBlank String unit,
        Long sourceInboundItemId,
        @NotBlank String warehouseName,
        String batchNo,
        @NotNull @Min(0) Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000") BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        BigDecimal weightTon,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        BigDecimal amount
) {
    public SalesOrderItemRequest(String materialCode,
                                 String brand,
                                 String category,
                                 String material,
                                 String spec,
                                 String length,
                                 String unit,
                                 Long sourceInboundItemId,
                                 String warehouseName,
                                 String batchNo,
                                 Integer quantity,
                                 String quantityUnit,
                                 BigDecimal pieceWeightTon,
                                 Integer piecesPerBundle,
                                 BigDecimal weightTon,
                                 BigDecimal unitPrice,
                                 BigDecimal amount) {
        this(null, materialCode, brand, category, material, spec, length, unit, sourceInboundItemId, warehouseName,
                batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
    }
}
