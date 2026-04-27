package com.leo.erp.statement.supplier.web.dto;

import java.math.BigDecimal;

public record SupplierStatementItemResponse(
        Long id,
        Integer lineNo,
        String sourceNo,
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
        BigDecimal amount
) {
}
