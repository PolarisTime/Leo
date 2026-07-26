package com.leo.erp.sales.api;

import java.math.BigDecimal;

public record SalesOrderSourceItemSnapshot(
        Long id,
        Integer lineNo,
        Long materialId,
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
        BigDecimal amount,
        BigDecimal originalWeightTon
) {
}
