package com.leo.erp.purchase.inbound.web.dto;

import java.math.BigDecimal;

public record PurchaseInboundItemResponse(
        Long id,
        Integer lineNo,
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
        Integer remainingQuantity,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
