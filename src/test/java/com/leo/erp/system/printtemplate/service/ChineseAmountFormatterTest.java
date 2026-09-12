package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChineseAmountFormatterTest {

    @ParameterizedTest
    @CsvSource({
            "0, 零元整",
            "1000, 壹仟元整",
            "1000.00, 壹仟元整",
            "10, 壹拾元整",
            "100, 壹佰元整",
            "101, 壹佰零壹元整",
            "1001, 壹仟零壹元整",
            "10000, 壹万元整",
            "100000, 壹拾万元整",
            "100000001, 壹亿零壹元整",
            "100010000, 壹亿零壹万元整",
            "250.50, 贰佰伍拾元伍角",
            "250.05, 贰佰伍拾元零伍分",
            "250.55, 贰佰伍拾元伍角伍分",
            "0.05, 零元零伍分",
            "0.50, 零元伍角",
            "12345678.90, 壹仟贰佰叁拾肆万伍仟陆佰柒拾捌元玖角",
            "999999999999.99, 玖仟玖佰玖拾玖亿玖仟玖佰玖拾玖万玖仟玖佰玖拾玖元玖角玖分"
    })
    void toWordsShouldConvertAmount(String raw, String expected) {
        assertThat(ChineseAmountFormatter.toWords(new BigDecimal(raw))).isEqualTo(expected);
    }

    @Test
    void toWordsShouldTreatNullAsZero() {
        assertThat(ChineseAmountFormatter.toWords(null)).isEqualTo("零元整");
    }

    @ParameterizedTest
    @CsvSource({
            "1000.00, 1000",
            "250.50, 250.50",
            "0.00, 0",
            "0.05, 0.05"
    })
    void plainShouldStripRedundantDecimals(String raw, String expected) {
        assertThat(ChineseAmountFormatter.plain(new BigDecimal(raw))).isEqualTo(expected);
    }
}
