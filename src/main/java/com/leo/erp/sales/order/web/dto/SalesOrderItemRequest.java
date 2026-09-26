package com.leo.erp.sales.order.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalesOrderItemRequest(
        Long id,
        Long materialId,
        @NotBlank String materialCode,
        @NotBlank String brand,
        @NotBlank String category,
        @NotBlank String material,
        @NotBlank String spec,
        String length,
        @NotBlank String unit,
        Long sourceInboundItemId,
        Long sourcePurchaseOrderItemId,
        Long warehouseId,
        @NotBlank String warehouseName,
        String batchNo,
        @NotNull @Min(0) Integer quantity,
        String quantityUnit,
        @NotNull @DecimalMin("0.000")
        @Digits(integer = 10, fraction = 8, message = "件重整数位不能超过10位，小数位不能超过8位")
        BigDecimal pieceWeightTon,
        @NotNull @Min(0) Integer piecesPerBundle,
        @Digits(integer = 10, fraction = 8, message = "重量整数位不能超过10位，小数位不能超过8位")
        BigDecimal weightTon,
        @NotNull @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2, message = "单价整数位不能超过10位，小数位不能超过2位")
        BigDecimal unitPrice,
        @Digits(integer = 12, fraction = 2, message = ValidationMessages.AMOUNT_PRECISION)
        BigDecimal amount
) {
    public SalesOrderItemRequest(Long id,
                                 String materialCode,
                                 String brand,
                                 String category,
                                 String material,
                                 String spec,
                                 String length,
                                 String unit,
                                 Long sourceInboundItemId,
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
        this(id, null, materialCode, brand, category, material, spec, length, unit, sourceInboundItemId,
                sourcePurchaseOrderItemId, null, warehouseName, batchNo, quantity, quantityUnit,
                pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
    }

    public SalesOrderItemRequest(String materialCode,
                                 String brand,
                                 String category,
                                 String material,
                                 String spec,
                                 String length,
                                 String unit,
                                 Long sourceInboundItemId,
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
        this(null, null, materialCode, brand, category, material, spec, length, unit, sourceInboundItemId,
                sourcePurchaseOrderItemId, null, warehouseName,
                batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
    }

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
        this(materialCode, brand, category, material, spec, length, unit, sourceInboundItemId, null, warehouseName,
                batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
    }
}
