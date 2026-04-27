package com.leo.erp.contract.purchase.web.dto;

import java.math.BigDecimal;

public record PurchaseContractItemResponse(
        Long id,
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
        BigDecimal amount
) {
}
