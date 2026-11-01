package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TradeItemCalculator 精度与空值回归测试。
 */
class TradeItemCalculatorTest {

    @Test
    void calculateWeightTon_shouldMultiplyQuantityByPieceWeight() {
        assertThat(TradeItemCalculator.calculateWeightTon(5, new BigDecimal("1.250")))
                .isEqualByComparingTo("6.25000000");
    }

    @Test
    void calculateWeightTon_shouldUseZeroForNullQuantity() {
        assertThat(TradeItemCalculator.calculateWeightTon(null, new BigDecimal("1.250")))
                .isEqualByComparingTo("0.00000000");
    }

    @Test
    void calculateWeightTon_shouldUseZeroForNullPieceWeight() {
        assertThat(TradeItemCalculator.calculateWeightTon(5, null))
                .isEqualByComparingTo("0.00000000");
    }

    @Test
    void calculateAmount_shouldMultiplyWeightByUnitPrice() {
        assertThat(TradeItemCalculator.calculateAmount(new BigDecimal("6.250"), new BigDecimal("4000.00")))
                .isEqualByComparingTo("25000.00");
    }

    @Test
    void calculateAmount_shouldRoundHalfUpOnDecimalMidpoint() {
        // 前端 double + toFixed(2) 会得到 33333.32；十进制 HALF_UP 必须与后端一致为 33333.33。
        assertThat(TradeItemCalculator.calculateAmount(new BigDecimal("9.995"), new BigDecimal("3335")))
                .isEqualByComparingTo("33333.33");
        assertThat(TradeItemCalculator.calculateAmount(new BigDecimal("1.005"), new BigDecimal("1")))
                .isEqualByComparingTo("1.01");
        assertThat(TradeItemCalculator.calculateAmount(new BigDecimal("2.675"), new BigDecimal("1")))
                .isEqualByComparingTo("2.68");
        assertThat(TradeItemCalculator.calculateAmount(new BigDecimal("0.125"), new BigDecimal("1")))
                .isEqualByComparingTo("0.13");
    }

    @Test
    void calculateAmount_shouldUseZeroForNullWeight() {
        assertThat(TradeItemCalculator.calculateAmount(null, new BigDecimal("4000.00")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void calculateAmount_shouldUseZeroForNullUnitPrice() {
        assertThat(TradeItemCalculator.calculateAmount(new BigDecimal("6.250"), null))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void scaleWeightTon_shouldKeepEightDecimalPlaces() {
        assertThat(TradeItemCalculator.scaleWeightTon(new BigDecimal("1.250000001")))
                .isEqualByComparingTo("1.25000000");
    }

    @Test
    void scaleAmount_shouldRoundHalfUpToTwoDecimalPlaces() {
        assertThat(TradeItemCalculator.scaleAmount(new BigDecimal("25000.005")))
                .isEqualByComparingTo("25000.01");
    }

    @Test
    void normalizeQuantityUnit_shouldDefaultToPieceForNull() {
        assertThat(TradeItemCalculator.normalizeQuantityUnit(null)).isEqualTo("件");
    }

    @Test
    void normalizeQuantityUnit_shouldDefaultToPieceForBlank() {
        assertThat(TradeItemCalculator.normalizeQuantityUnit("   ")).isEqualTo("件");
    }

    @Test
    void normalizeQuantityUnit_shouldKeepProvidedValue() {
        assertThat(TradeItemCalculator.normalizeQuantityUnit("吨")).isEqualTo("吨");
    }

    @Test
    void calculateAveragePieceWeightTon_shouldReturnZeroForNonPositiveQuantity() {
        assertThat(TradeItemCalculator.calculateAveragePieceWeightTon(0, new BigDecimal("6.250")))
                .isEqualByComparingTo("0.00000000");
    }
}
