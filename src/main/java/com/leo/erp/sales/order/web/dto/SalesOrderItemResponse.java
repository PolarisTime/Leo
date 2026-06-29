package com.leo.erp.sales.order.web.dto;

import java.math.BigDecimal;

public record SalesOrderItemResponse(
        Long id,
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
        this(id, lineNo, materialCode, brand, category, material, spec, length, unit,
                sourceInboundItemId, sourcePurchaseOrderItemId, null, null, warehouseName, batchNo,
                quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount,
                originalWeightTon);
    }
}
