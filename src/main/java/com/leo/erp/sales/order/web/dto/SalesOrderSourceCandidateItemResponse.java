package com.leo.erp.sales.order.web.dto;

import java.math.BigDecimal;

public record SalesOrderSourceCandidateItemResponse(
        Long id,
        Integer lineNo,
        Long sourceInboundItemId,
        Long sourcePurchaseOrderItemId,
        Long rootPurchaseOrderItemId,
        Integer sourceLineNo,
        String inboundNo,
        Long materialId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long warehouseId,
        String warehouseName,
        String batchNo,
        String batchNoNormalized,
        Integer quantity,
        Integer remainingQuantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal remainingWeightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
