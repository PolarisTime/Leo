package com.leo.erp.purchase.refund.web.dto;

import java.math.BigDecimal;

public record PurchaseRefundItemResponse(
        Long id,
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
        BigDecimal amount
) {
}
