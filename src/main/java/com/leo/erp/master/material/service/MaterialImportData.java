package com.leo.erp.master.material.service;

import com.leo.erp.master.material.domain.MaterialTypes;
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
        String remark,
        String materialType
) {

    static final String TYPE_PHYSICAL = MaterialTypes.PHYSICAL;
    static final String TYPE_EXPENSE = MaterialTypes.EXPENSE;

    boolean isExpense() {
        return TYPE_EXPENSE.equals(materialType);
    }
}
