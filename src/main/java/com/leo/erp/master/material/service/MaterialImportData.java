package com.leo.erp.master.material.service;

import java.math.BigDecimal;

record MaterialImportData(
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
        String remark
) {
}
