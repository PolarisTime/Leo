package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印数据格式化纯函数的边界：空值、非法值、十进制精度与金额舍入口径。
 */
class PrintItemFormattingTest {

    @Test
    void sumDecimalShouldAccumulateExactly() {
        List<Map<String, String>> items = List.of(
                Map.of("weightTon", "0.1"),
                Map.of("weightTon", "0.2"),
                Map.of("weightTon", "0.3")
        );

        assertThat(PrintItemFormatting.sumDecimal(items, "weightTon"))
                .isEqualByComparingTo(new BigDecimal("0.6"));
    }

    @Test
    void sumIntegerShouldTruncateEachRow() {
        List<Map<String, String>> items = List.of(
                Map.of("quantity", "1.9"),
                Map.of("quantity", "2.1")
        );

        assertThat(PrintItemFormatting.sumInteger(items, "quantity")).isEqualTo(3);
    }

    @Test
    void firstPositiveDecimalShouldSkipZeroAndNegative() {
        List<Map<String, String>> items = List.of(
                Map.of("amount", "0"),
                Map.of("amount", "-1.5"),
                Map.of("amount", "12.34")
        );

        assertThat(PrintItemFormatting.firstPositiveDecimal(items, "amount"))
                .isEqualByComparingTo(new BigDecimal("12.34"));
        assertThat(PrintItemFormatting.firstPositiveDecimal(List.of(Map.of("amount", "0")), "amount"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void decimalOfShouldFallBackToZero() {
        assertThat(PrintItemFormatting.decimalOf(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(PrintItemFormatting.decimalOf("  ")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(PrintItemFormatting.decimalOf("abc")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(PrintItemFormatting.decimalOf(" 12.5 ")).isEqualByComparingTo(new BigDecimal("12.5"));
    }

    @Test
    void plainAmountShouldRoundWithHalfUp() {
        assertThat(PrintItemFormatting.plainAmount(new BigDecimal("2.675"))).isEqualTo("2.68");
        assertThat(PrintItemFormatting.plainAmount(null)).isEqualTo("0.00");
    }

    @Test
    void plainNumberShouldStripTrailingZeros() {
        assertThat(PrintItemFormatting.plainNumber(new BigDecimal("1.2000"))).isEqualTo("1.2");
        assertThat(PrintItemFormatting.plainNumber(null)).isEqualTo("0");
    }

    @Test
    void normalizeTextShouldTrimAndNullToEmpty() {
        assertThat(PrintItemFormatting.normalizeText(null)).isEmpty();
        assertThat(PrintItemFormatting.normalizeText("  abc  ")).isEqualTo("abc");
    }
}
