package com.leo.erp.statement.supplier.web.dto;

import java.math.BigDecimal;

public record SupplierStatementItemResponse(
        Long id,
        Integer lineNo,
        String sourceNo,
        Long sourceInboundItemId,
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
        BigDecimal weighWeightTon,
        BigDecimal weightAdjustmentTon,
        BigDecimal weightAdjustmentAmount,
        BigDecimal unitPrice,
        BigDecimal amount,
        Long materialId,
        Long warehouseId,
        String batchNoNormalized
) {
    public SupplierStatementItemResponse(Long id,
                                         Integer lineNo,
                                         String sourceNo,
                                         Long sourceInboundItemId,
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
                                         BigDecimal weighWeightTon,
                                         BigDecimal weightAdjustmentTon,
                                         BigDecimal weightAdjustmentAmount,
                                         BigDecimal unitPrice,
                                         BigDecimal amount) {
        this(id, lineNo, sourceNo, sourceInboundItemId, materialCode, brand, category, material, spec, length, unit,
                batchNo, quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, weighWeightTon,
                weightAdjustmentTon, weightAdjustmentAmount, unitPrice, amount, null, null, null);
    }
}
