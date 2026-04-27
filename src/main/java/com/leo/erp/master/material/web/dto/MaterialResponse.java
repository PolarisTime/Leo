package com.leo.erp.master.material.web.dto;

import java.math.BigDecimal;

public record MaterialResponse(
        Long id,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        String unit,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal unitPrice,
        Boolean batchNoEnabled,
        String remark
) {
}
