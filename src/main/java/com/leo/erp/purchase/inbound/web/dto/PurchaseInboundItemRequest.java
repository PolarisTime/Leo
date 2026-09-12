package com.leo.erp.purchase.inbound.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseInboundItemRequest(
        Long id,
        @NotNull Long materialId,
        @NotBlank String materialCode,
        @NotBlank String brand,
        @NotBlank String category,
        @NotBlank String material,
        @NotBlank String spec,
        String length,
        @NotBlank String unit,
        @NotNull(message = "来源采购订单明细不能为空")
        Long sourcePurchaseOrderItemId,
        @NotNull Long warehouseId,
        @NotBlank String warehouseName,
        String settlementMode,
        String batchNo,
        @NotNull @Min(value = 1, message = "入库数量必须大于0") Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000")
        @Digits(integer = 10, fraction = 8, message = "件重整数位不能超过10位，小数位不能超过8位")
        BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        @Digits(integer = 10, fraction = 8, message = "重量整数位不能超过10位，小数位不能超过8位")
        BigDecimal weightTon,
        @Digits(integer = 10, fraction = 8, message = "过磅重量整数位不能超过10位，小数位不能超过8位")
        BigDecimal weighWeightTon,
        @Digits(integer = 10, fraction = 8, message = "重量调整整数位不能超过10位，小数位不能超过8位")
        BigDecimal weightAdjustmentTon,
        @Digits(integer = 12, fraction = 2, message = "重量调整金额整数位不能超过12位，小数位不能超过2位")
        BigDecimal weightAdjustmentAmount,
        @NotNull @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2, message = "单价整数位不能超过10位，小数位不能超过2位")
        BigDecimal unitPrice,
        @Digits(integer = 12, fraction = 2, message = "金额整数位不能超过12位，小数位不能超过2位")
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
                                      String settlementMode,
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
        this(id, null, materialCode, brand, category, material, spec, length, unit, sourcePurchaseOrderItemId,
                null, warehouseName, settlementMode, batchNo, quantity, quantityUnit, pieceWeightTon,
                piecesPerBundle, weightTon, weighWeightTon, weightAdjustmentTon, weightAdjustmentAmount,
                unitPrice, amount);
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
                                      BigDecimal weighWeightTon,
                                      BigDecimal weightAdjustmentTon,
                                      BigDecimal weightAdjustmentAmount,
                                      BigDecimal unitPrice,
                                      BigDecimal amount) {
        this(id, null, materialCode, brand, category, material, spec, length, unit, sourcePurchaseOrderItemId,
                null, warehouseName,
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
        this(id, null, materialCode, brand, category, material, spec, length, unit, sourcePurchaseOrderItemId,
                null, warehouseName,
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
        this(null, null, materialCode, brand, category, material, spec, length, unit, sourcePurchaseOrderItemId,
                null, warehouseName,
                null, batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, null, null, null,
                unitPrice, amount);
    }
}
