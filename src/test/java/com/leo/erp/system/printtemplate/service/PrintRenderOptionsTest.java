package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrintRenderOptions#from 对 splitPieceCount 的解析与归一化测试。
 */
class PrintRenderOptionsTest {

    @Test
    void fromShouldParseNumericSplitPieceCount() {
        Map<String, Object> options = new HashMap<>();
        options.put("splitPieceCount", 25);

        assertThat(PrintRenderOptions.from(options).splitPieceCount()).isEqualTo(25);
    }

    @Test
    void fromShouldParseStringSplitPieceCount() {
        Map<String, Object> options = new HashMap<>();
        options.put("splitPieceCount", "25");

        assertThat(PrintRenderOptions.from(options).splitPieceCount()).isEqualTo(25);
    }

    @Test
    void fromShouldTreatNonPositiveOrInvalidSplitPieceCountAsNull() {
        Map<String, Object> zero = new HashMap<>();
        zero.put("splitPieceCount", 0);
        assertThat(PrintRenderOptions.from(zero).splitPieceCount()).isNull();

        Map<String, Object> negative = new HashMap<>();
        negative.put("splitPieceCount", -3);
        assertThat(PrintRenderOptions.from(negative).splitPieceCount()).isNull();

        Map<String, Object> nonNumeric = new HashMap<>();
        nonNumeric.put("splitPieceCount", "abc");
        assertThat(PrintRenderOptions.from(nonNumeric).splitPieceCount()).isNull();

        Map<String, Object> fractional = new HashMap<>();
        fractional.put("splitPieceCount", 1.5);
        assertThat(PrintRenderOptions.from(fractional).splitPieceCount()).isNull();
    }

    @Test
    void fromShouldDefaultSplitPieceCountToNullWhenAbsent() {
        assertThat(PrintRenderOptions.from(new HashMap<>()).splitPieceCount()).isNull();
        assertThat(PrintRenderOptions.defaults().splitPieceCount()).isNull();
    }

    @Test
    void constructorShouldNormalizeNonPositiveSplitPieceCount() {
        PrintRenderOptions options = new PrintRenderOptions(
                false, false, true, "", Map.of(), Map.of(), java.util.List.of(), null, 0, null);

        assertThat(options.splitPieceCount()).isNull();
    }

    @Test
    void fromShouldKeepSplitItemIdsNullWhenAbsentForLegacySplitAll() {
        Map<String, Object> options = new HashMap<>();
        options.put("splitPieceCount", 25);

        assertThat(PrintRenderOptions.from(options).splitItemIds()).isNull();
        assertThat(PrintRenderOptions.defaults().splitItemIds()).isNull();
    }

    @Test
    void fromShouldParseSplitItemIdsAndKeepEmptyListAsNoSplit() {
        Map<String, Object> selected = new HashMap<>();
        selected.put("splitItemIds", java.util.Arrays.asList(1001L, " 1002 ", 1002L, null, " "));
        assertThat(PrintRenderOptions.from(selected).splitItemIds())
                .containsExactly("1001", "1002");

        Map<String, Object> empty = new HashMap<>();
        empty.put("splitItemIds", java.util.List.of());
        assertThat(PrintRenderOptions.from(empty).splitItemIds()).isEmpty();
    }

    @Test
    void jsonShouldDeserializeSplitItemIdsAlongWithSplitPieceCount() throws Exception {
        PrintRenderOptions options = new ObjectMapper().readValue(
                """
                {"splitPieceCount":25,"splitItemIds":["357099695337644032","357099695341838336"]}
                """,
                PrintRenderOptions.class);

        assertThat(options.splitPieceCount()).isEqualTo(25);
        assertThat(options.splitItemIds())
                .containsExactly("357099695337644032", "357099695341838336");
    }

    @Test
    void jsonShouldKeepSplitItemIdsNullWhenAbsent() throws Exception {
        PrintRenderOptions options = new ObjectMapper().readValue(
                """
                {"splitPieceCount":25}
                """,
                PrintRenderOptions.class);

        assertThat(options.splitItemIds()).isNull();
    }

    @Test
    void constructorShouldNormalizeSplitItemIds() {
        PrintRenderOptions options = new PrintRenderOptions(
                false, false, true, "", Map.of(), Map.of(), java.util.List.of(), null, 25,
                java.util.Arrays.asList(" 7 ", "7", "", null));

        assertThat(options.splitItemIds()).containsExactly("7");
    }
}
