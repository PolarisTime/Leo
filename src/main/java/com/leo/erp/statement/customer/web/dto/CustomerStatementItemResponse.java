package com.leo.erp.statement.customer.web.dto;

import java.math.BigDecimal;

public record CustomerStatementItemResponse(
        Long id,
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
        String batchNo,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount,
        Long customerId,
        Long projectId,
        Long materialId,
        Long warehouseId,
        String batchNoNormalized
) {
    public CustomerStatementItemResponse(Long id,
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
                                         String batchNo,
                                         Integer quantity,
                                         String quantityUnit,
                                         BigDecimal pieceWeightTon,
                                         Integer piecesPerBundle,
                                         BigDecimal weightTon,
                                         BigDecimal unitPrice,
                                         BigDecimal amount) {
        this(id, lineNo, sourceNo, sourceSalesOrderItemId, materialCode, brand, category, material, spec, length,
                unit, batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount,
                null, null, null, null, null);
    }
}
