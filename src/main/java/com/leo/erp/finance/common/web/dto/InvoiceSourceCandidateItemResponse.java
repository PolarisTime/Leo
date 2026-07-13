package com.leo.erp.finance.common.web.dto;

import java.math.BigDecimal;

public record InvoiceSourceCandidateItemResponse(
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
}
