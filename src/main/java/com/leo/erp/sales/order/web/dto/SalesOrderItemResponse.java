package com.leo.erp.sales.order.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

public record SalesOrderItemResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        Integer lineNo,
        @JsonSerialize(using = ToStringSerializer.class) Long materialId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceInboundItemId,
        @JsonSerialize(using = ToStringSerializer.class) Long sourcePurchaseOrderItemId,
        @JsonSerialize(using = ToStringSerializer.class) Long settlementCompanyId,
        String settlementCompanyName,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        String warehouseName,
        String batchNo,
        String batchNoNormalized,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount,
        BigDecimal originalWeightTon
) {
    public SalesOrderItemResponse(Long id,
                                  Integer lineNo,
                                  String materialCode,
                                  String brand,
                                  String category,
                                  String material,
                                  String spec,
                                  String length,
                                  String unit,
                                  Long sourceInboundItemId,
                                  Long sourcePurchaseOrderItemId,
                                  Long settlementCompanyId,
                                  String settlementCompanyName,
                                  String warehouseName,
                                  String batchNo,
                                  Integer quantity,
                                  String quantityUnit,
                                  BigDecimal pieceWeightTon,
                                  Integer piecesPerBundle,
                                  BigDecimal weightTon,
                                  BigDecimal unitPrice,
                                  BigDecimal amount,
                                  BigDecimal originalWeightTon) {
        this(id, lineNo, null, materialCode, brand, category, material, spec, length, unit,
                sourceInboundItemId, sourcePurchaseOrderItemId, settlementCompanyId, settlementCompanyName,
                null, warehouseName, batchNo, null, quantity, quantityUnit, pieceWeightTon, piecesPerBundle,
                weightTon, unitPrice, amount, originalWeightTon);
    }

    public SalesOrderItemResponse(Long id,
                                  Integer lineNo,
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
                                  BigDecimal amount,
                                  BigDecimal originalWeightTon) {
        this(id, lineNo, null, materialCode, brand, category, material, spec, length, unit,
                sourceInboundItemId, sourcePurchaseOrderItemId, null, null, null, warehouseName, batchNo, null,
                quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount,
                originalWeightTon);
    }
}
