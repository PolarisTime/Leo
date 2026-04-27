package com.leo.erp.common.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TradeItemCalculator {

    public static final String QUANTITY_UNIT_PIECE = "件";

    private TradeItemCalculator() {
    }

    public static String normalizeQuantityUnit(String ignored) {
        return QUANTITY_UNIT_PIECE;
    }

    public static BigDecimal calculateWeightTon(Integer quantity, BigDecimal pieceWeightTon) {
        BigDecimal safeQuantity = BigDecimal.valueOf(quantity == null ? 0L : quantity.longValue());
        BigDecimal safePieceWeightTon = pieceWeightTon == null ? BigDecimal.ZERO : pieceWeightTon;
        return safeQuantity.multiply(safePieceWeightTon).setScale(3, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateAmount(BigDecimal weightTon, BigDecimal unitPrice) {
        BigDecimal safeWeightTon = weightTon == null ? BigDecimal.ZERO : weightTon;
        BigDecimal safeUnitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return safeWeightTon.multiply(safeUnitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
