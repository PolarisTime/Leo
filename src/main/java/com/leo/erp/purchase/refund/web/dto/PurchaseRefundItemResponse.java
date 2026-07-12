package com.leo.erp.purchase.refund.web.dto;

import java.math.BigDecimal;

public record PurchaseRefundItemResponse(
        Long id,
        Integer lineNo,
        Long sourcePurchaseOrderItemId,
        Long materialId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        Long warehouseId,
        String warehouseName,
        String batchNo,
        String batchNoNormalized,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
    public PurchaseRefundItemResponse(Long id,
                                      Integer lineNo,
                                      Long sourcePurchaseOrderItemId,
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
        this(id, lineNo, sourcePurchaseOrderItemId, null, materialCode, brand, category, material,
                spec, length, unit, null, warehouseName, batchNo, null, quantity, quantityUnit,
                pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
    }
}
