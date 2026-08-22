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
        String remark,
        String materialType
) {

    static final String TYPE_PHYSICAL = "实体商品";
    static final String TYPE_EXPENSE = "附加费用";

    boolean isExpense() {
        return TYPE_EXPENSE.equals(materialType);
    }
}
