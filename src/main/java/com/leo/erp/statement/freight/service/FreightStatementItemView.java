package com.leo.erp.statement.freight.service;

import java.math.BigDecimal;

public record FreightStatementItemView(
        Long id,
        Integer lineNo,
        String sourceNo,
        String customerName,
        String projectName,
        String materialCode,
        String materialName,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        String batchNo,
        BigDecimal weightTon,
        String warehouseName
) {
}
