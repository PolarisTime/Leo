package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;

public record PurchaseOrderItemResponse(
        Long id,
        Integer lineNo,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        String warehouseName,
        String batchNo,
        Integer remainingQuantity,
        Integer salesRemainingQuantity,
        BigDecimal salesRemainingWeightTon,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
