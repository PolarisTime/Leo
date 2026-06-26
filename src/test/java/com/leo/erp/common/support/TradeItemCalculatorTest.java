package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TradeItemCalculatorTest {

    @Test
    void shouldNormalizeQuantityUnitToPieceWhenNull() {
        assertThat(TradeItemCalculator.normalizeQuantityUnit(null)).isEqualTo("件");
    }

    @Test
    void shouldNormalizeQuantityUnitToPieceWhenBlank() {
        assertThat(TradeItemCalculator.normalizeQuantityUnit("  ")).isEqualTo("件");
    }

    @Test
    void shouldReturnProvidedQuantityUnit() {
        assertThat(TradeItemCalculator.normalizeQuantityUnit("吨")).isEqualTo("吨");
    }

    @Test
    void shouldReturnZeroForNullBigDecimal() {
        assertThat(TradeItemCalculator.safeBigDecimal(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnValueForNonNullBigDecimal() {
        assertThat(TradeItemCalculator.safeBigDecimal(new BigDecimal("5.000")))
                .isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void shouldScaleWeightTon() {
        BigDecimal result = TradeItemCalculator.scaleWeightTon(new BigDecimal("5.123456789"));
        assertThat(result.toPlainString()).isEqualTo("5.12345679");
    }

    @Test
    void shouldRoundTinyWeightAtInternalScale() {
        assertThat(TradeItemCalculator.scaleWeightTon(new BigDecimal("0.000000004")).toPlainString())
                .isEqualTo("0.00000000");
        assertThat(TradeItemCalculator.scaleWeightTon(new BigDecimal("0.000000005")).toPlainString())
                .isEqualTo("0.00000001");
    }

    @Test
    void shouldScaleWeightTonFromNull() {
        assertThat(TradeItemCalculator.scaleWeightTon(null).toPlainString()).isEqualTo("0.00000000");
    }

    @Test
    void shouldScaleAmount() {
        BigDecimal result = TradeItemCalculator.scaleAmount(new BigDecimal("100.567"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.57"));
    }

    @Test
    void shouldScaleAmountFromNull() {
        assertThat(TradeItemCalculator.scaleAmount(null)).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void shouldCalculateWeightTon() {
        BigDecimal result = TradeItemCalculator.calculateWeightTon(10, new BigDecimal("0.500"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void shouldCalculateWeightTonWithNullQuantity() {
        BigDecimal result = TradeItemCalculator.calculateWeightTon(null, new BigDecimal("0.500"));
        assertThat(result.toPlainString()).isEqualTo("0.00000000");
    }

    @Test
    void shouldCalculateWeightTonWithNullPieceWeight() {
        BigDecimal result = TradeItemCalculator.calculateWeightTon(10, null);
        assertThat(result.toPlainString()).isEqualTo("0.00000000");
    }

    @Test
    void shouldKeepSmallPieceWeightPrecision() {
        BigDecimal result = TradeItemCalculator.calculateWeightTon(1, new BigDecimal("0.005555"));
        assertThat(result.toPlainString()).isEqualTo("0.00555500");
    }

    @Test
    void shouldCalculateMinimumRepresentableWeightTon() {
        BigDecimal result = TradeItemCalculator.calculateWeightTon(1, new BigDecimal("0.000000005"));
        assertThat(result.toPlainString()).isEqualTo("0.00000001");
    }

    @Test
    void shouldCalculateAveragePieceWeightTon() {
        BigDecimal result = TradeItemCalculator.calculateAveragePieceWeightTon(10, new BigDecimal("5.000"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.500"));
    }

    @Test
    void shouldReturnZeroAverageWhenQuantityIsZero() {
        BigDecimal result = TradeItemCalculator.calculateAveragePieceWeightTon(0, new BigDecimal("5.000"));
        assertThat(result.toPlainString()).isEqualTo("0.00000000");
    }

    @Test
    void shouldReturnZeroAverageWhenQuantityIsNull() {
        BigDecimal result = TradeItemCalculator.calculateAveragePieceWeightTon(null, new BigDecimal("5.000"));
        assertThat(result.toPlainString()).isEqualTo("0.00000000");
    }

    @Test
    void shouldCalculateAmount() {
        BigDecimal result = TradeItemCalculator.calculateAmount(new BigDecimal("5.000"), new BigDecimal("100.00"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void shouldCalculateAmountWithNulls() {
        assertThat(TradeItemCalculator.calculateAmount(null, new BigDecimal("100.00")))
                .isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(TradeItemCalculator.calculateAmount(new BigDecimal("5.000"), null))
                .isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void shouldCalculateRepresentableAveragePieceWeightTon() {
        BigDecimal result = TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(10, new BigDecimal("5.000"));
        assertThat(result).isNotNull();
        assertThat(TradeItemCalculator.calculateWeightTon(10, result))
                .isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void shouldReturnNullRepresentableWhenQuantityIsZero() {
        assertThat(TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(0, new BigDecimal("5.000"))).isNull();
    }

    @Test
    void shouldReturnNullRepresentableWhenWeightIsZero() {
        assertThat(TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(10, BigDecimal.ZERO)).isNull();
    }

    @Test
    void shouldHaveQuantityUnitPiece() {
        assertThat(TradeItemCalculator.QUANTITY_UNIT_PIECE).isEqualTo("件");
    }
}
