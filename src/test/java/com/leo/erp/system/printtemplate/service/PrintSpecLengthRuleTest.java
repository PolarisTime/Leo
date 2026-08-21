package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrintSpecLengthRule 极端情况测试：
 * 12 米拼接 *12、9 米不处理、空值、已带后缀不重复、非法长度。
 */
class PrintSpecLengthRuleTest {

    @Test
    void apply_shouldAppendSuffixForTwelveMeter() {
        assertThat(PrintSpecLengthRule.apply("HRB400", "12米")).isEqualTo("HRB400*12");
    }

    @Test
    void apply_shouldKeepSpecForOtherLengths() {
        // 9 米无需操作
        assertThat(PrintSpecLengthRule.apply("HRB400", "9米")).isEqualTo("HRB400");
        assertThat(PrintSpecLengthRule.apply("HRB400", "6米")).isEqualTo("HRB400");
    }

    @Test
    void apply_shouldNotDuplicateSuffix() {
        assertThat(PrintSpecLengthRule.apply("HRB400*12", "12米")).isEqualTo("HRB400*12");
    }

    @Test
    void apply_shouldHandleBlankAndNull() {
        assertThat(PrintSpecLengthRule.apply(null, "12米")).isEmpty();
        assertThat(PrintSpecLengthRule.apply("", "12米")).isEmpty();
        assertThat(PrintSpecLengthRule.apply("HRB400", null)).isEqualTo("HRB400");
        assertThat(PrintSpecLengthRule.apply(null, null)).isEmpty();
    }

    @Test
    void apply_shouldTrimInputs() {
        assertThat(PrintSpecLengthRule.apply(" HRB400 ", " 12米 ")).isEqualTo("HRB400*12");
    }

    @Test
    void apply_shouldRejectNonExactLengthMatch() {
        // 非精确"12米"（如"12M"、"112米"、纯数字）不拼接
        assertThat(PrintSpecLengthRule.apply("HRB400", "12")).isEqualTo("HRB400");
        assertThat(PrintSpecLengthRule.apply("HRB400", "12M")).isEqualTo("HRB400");
        assertThat(PrintSpecLengthRule.apply("HRB400", "112米")).isEqualTo("HRB400");
    }
}
