package com.leo.erp.purchase.inbound.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseInboundItemRequest(
        Long id,
        @NotBlank String materialCode,
        @NotBlank String brand,
        @NotBlank String category,
        @NotBlank String material,
        @NotBlank String spec,
        String length,
        @NotBlank String unit,
        Long sourcePurchaseOrderItemId,
        String warehouseName,
        String settlementMode,
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
    public PurchaseInboundItemRequest(Long id,
                                      String materialCode,
                                      String brand,
                                      String category,
                                      String material,
                                      String spec,
                                      String length,
                                      String unit,
                                      Long sourcePurchaseOrderItemId,
                                      String warehouseName,
                                      String batchNo,
                                      Integer quantity,
                                      String quantityUnit,
                                      BigDecimal pieceWeightTon,
                                      Integer piecesPerBundle,
                                      BigDecimal weightTon,
                                      BigDecimal weighWeightTon,
                                      BigDecimal weightAdjustmentTon,
                                      BigDecimal weightAdjustmentAmount,
                                      BigDecimal unitPrice,
                                      BigDecimal amount) {
        this(id, materialCode, brand, category, material, spec, length, unit, sourcePurchaseOrderItemId, warehouseName,
                null, batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, weighWeightTon,
                weightAdjustmentTon, weightAdjustmentAmount, unitPrice, amount);
    }

    public PurchaseInboundItemRequest(Long id,
                                      String materialCode,
                                      String brand,
                                      String category,
                                      String material,
                                      String spec,
                                      String length,
                                      String unit,
                                      Long sourcePurchaseOrderItemId,
                                      String warehouseName,
                                      String batchNo,
                                      Integer quantity,
                                      String quantityUnit,
                                      BigDecimal pieceWeightTon,
                                      Integer piecesPerBundle,
                                      BigDecimal weightTon,
                                      BigDecimal unitPrice,
                                      BigDecimal amount) {
        this(id, materialCode, brand, category, material, spec, length, unit, sourcePurchaseOrderItemId, warehouseName,
                null, batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, null, null, null,
                unitPrice, amount);
    }

    public PurchaseInboundItemRequest(String materialCode,
                                      String brand,
                                      String category,
                                      String material,
                                      String spec,
                                      String length,
                                      String unit,
                                      Long sourcePurchaseOrderItemId,
                                      String warehouseName,
                                      String batchNo,
                                      Integer quantity,
                                      String quantityUnit,
                                      BigDecimal pieceWeightTon,
                                      Integer piecesPerBundle,
                                      BigDecimal weightTon,
                                      BigDecimal unitPrice,
                                      BigDecimal amount) {
        this(null, materialCode, brand, category, material, spec, length, unit, sourcePurchaseOrderItemId, warehouseName,
                null, batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, null, null, null,
                unitPrice, amount);
    }
}
