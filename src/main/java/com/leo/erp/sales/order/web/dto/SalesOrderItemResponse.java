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
