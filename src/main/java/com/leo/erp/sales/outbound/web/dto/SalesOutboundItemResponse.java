package com.leo.erp.sales.outbound.web.dto;

import java.math.BigDecimal;

public record SalesOutboundItemResponse(
        Long id,
        Integer lineNo,
        String sourceNo,
        Long sourceSalesOrderItemId,
        Long settlementCompanyId,
        String settlementCompanyName,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        String warehouseName,
        String batchNo,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
    public SalesOutboundItemResponse(Long id,
                                     Integer lineNo,
                                     String sourceNo,
                                     Long sourceSalesOrderItemId,
                                     String materialCode,
                                     String brand,
                                     String category,
                                     String material,
                                     String spec,
                                     String length,
                                     String unit,
                                     String warehouseName,
                                     String batchNo,
                                     Integer quantity,
                                     String quantityUnit,
                                     BigDecimal pieceWeightTon,
                                     Integer piecesPerBundle,
                                     BigDecimal weightTon,
                                     BigDecimal unitPrice,
                                     BigDecimal amount) {
        this(id, lineNo, sourceNo, sourceSalesOrderItemId, null, null, materialCode, brand, category,
                material, spec, length, unit, warehouseName, batchNo, quantity, quantityUnit,
                pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
    }
}
