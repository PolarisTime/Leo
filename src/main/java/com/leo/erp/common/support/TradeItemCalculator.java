package com.leo.erp.common.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TradeItemCalculator {

    public static final String QUANTITY_UNIT_PIECE = "件";

    private TradeItemCalculator() {
    }

    public static String normalizeQuantityUnit(String quantityUnit) {
        if (quantityUnit == null || quantityUnit.isBlank()) {
            return QUANTITY_UNIT_PIECE;
        }
        return quantityUnit;
    }

    public static BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal scaleWeightTon(BigDecimal value) {
        return safeBigDecimal(value).setScale(3, RoundingMode.HALF_UP);
    }

    public static BigDecimal scaleAmount(BigDecimal value) {
        return safeBigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateWeightTon(Integer quantity, BigDecimal pieceWeightTon) {
        BigDecimal safeQuantity = BigDecimal.valueOf(quantity == null ? 0L : quantity.longValue());
        BigDecimal safePieceWeightTon = pieceWeightTon == null ? BigDecimal.ZERO : pieceWeightTon;
        return scaleWeightTon(safeQuantity.multiply(safePieceWeightTon));
    }

    public static BigDecimal calculateAveragePieceWeightTon(Integer quantity, BigDecimal weightTon) {
        if (quantity == null || quantity <= 0) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
        return safeBigDecimal(weightTon)
                .divide(BigDecimal.valueOf(quantity.longValue()), 3, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateRepresentableAveragePieceWeightTon(Integer quantity, BigDecimal weightTon) {
        if (quantity == null || quantity <= 0) {
            return null;
        }
        BigDecimal scaledWeightTon = scaleWeightTon(weightTon);
        if (scaledWeightTon.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal pieceWeightTon = calculateAveragePieceWeightTon(quantity, scaledWeightTon);
        return calculateWeightTon(quantity, pieceWeightTon).compareTo(scaledWeightTon) == 0
                ? pieceWeightTon
                : null;
    }

    public static BigDecimal calculateAmount(BigDecimal weightTon, BigDecimal unitPrice) {
        BigDecimal safeWeightTon = weightTon == null ? BigDecimal.ZERO : weightTon;
        BigDecimal safeUnitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return scaleAmount(safeWeightTon.multiply(safeUnitPrice));
    }
}
