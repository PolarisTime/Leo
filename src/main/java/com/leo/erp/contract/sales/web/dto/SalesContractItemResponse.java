package com.leo.erp.contract.sales.web.dto;

import java.math.BigDecimal;

public record SalesContractItemResponse(
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
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
    public SalesContractItemResponse(Long id,
                                     Integer lineNo,
                                     String materialCode,
                                     String brand,
                                     String category,
                                     String material,
                                     String spec,
                                     String length,
                                     String unit,
                                     Integer quantity,
                                     String quantityUnit,
                                     BigDecimal pieceWeightTon,
                                     Integer piecesPerBundle,
                                     BigDecimal weightTon,
                                     BigDecimal unitPrice,
                                     BigDecimal amount) {
        this(id, lineNo, null, materialCode, brand, category, material, spec, length, unit, quantity, quantityUnit,
                pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
    }
}
