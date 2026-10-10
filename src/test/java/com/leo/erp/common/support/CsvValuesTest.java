package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 逗号分隔多值字段的读写边界回归: 空值、空白、重复与不可变性。
 */
class CsvValuesTest {

    @Test
    void join_nullAndEmpty_returnNull() {
        assertThat(CsvValues.join(null)).isNull();
        assertThat(CsvValues.join(List.of())).isNull();
    }

    @Test
    void join_blankOnly_returnNull() {
        assertThat(CsvValues.join(List.of("", "   ", "\t"))).isNull();
    }

    @Test
    void join_trimsAndDeduplicates_preservingOrder() {
        assertThat(CsvValues.join(List.of(" 沙钢 ", "永钢", "沙钢", "  ", "中天")))
                .isEqualTo("沙钢,永钢,中天");
    }

    @Test
    void join_skipsNullElements() {
        List<String> values = new ArrayList<>(Arrays.asList("沙钢", null, "永钢"));
        assertThat(CsvValues.join(values)).isEqualTo("沙钢,永钢");
    }

    @Test
    void split_nullAndBlank_returnEmptyList() {
        assertThat(CsvValues.split(null)).isEmpty();
        assertThat(CsvValues.split("   ")).isEmpty();
    }

    @Test
    void split_trimsIgnoringBlankSegmentsAndDuplicates() {
        assertThat(CsvValues.split(" 沙钢 , 永钢 ,,沙钢,")).containsExactly("沙钢", "永钢");
    }

    @Test
    void split_resultIsImmutable() {
        List<String> values = CsvValues.split("沙钢,永钢");

        assertThat(values).isUnmodifiable();
    }

    @Test
    void roundTrip_joinThenSplit_isStable() {
        List<String> original = List.of("沙钢", "永钢", "中天");

        assertThat(CsvValues.split(CsvValues.join(original))).isEqualTo(original);
    }
}
