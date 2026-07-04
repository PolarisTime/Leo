package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrintOptionsTest {

    @Test
    void shouldReturnDefaultsForUnsupportedRawItemOptions() {
        PrintItemOptions options = PrintItemOptions.from("invalid");

        assertThat(options).isEqualTo(PrintItemOptions.defaults());
    }

    @Test
    void shouldNormalizeItemOptionsFromMap() {
        PrintItemOptions options = PrintItemOptions.from(Map.of(
                "brandOverride", "  沙钢  ",
                "brandOverrides", Map.of(
                        " 抚顺新钢 ", " 抚新 ",
                        "blank", " "
                ),
                "brandOverridesByItemId", Map.of(
                        " 11 ", " 永钢 ",
                        12, "ignored",
                        "13", 14
                ),
                "itemOrder", List.of(" 12 ", "11", "12", "", 13)
        ));

        assertThat(options.brandOverride()).isEqualTo("沙钢");
        assertThat(options.brandOverrides()).containsOnly(Map.entry("抚顺新钢", "抚新"));
        assertThat(options.brandOverridesByItemId()).containsOnly(Map.entry("11", "永钢"));
        assertThat(options.itemOrder()).containsExactly("12", "11", "13");
    }

    @Test
    void shouldNormalizeNullAndEmptyConstructorValues() {
        PrintItemOptions options = new PrintItemOptions(
                null,
                null,
                Map.of(" ", "ignored"),
                List.of(" ", "A", "A")
        );

        assertThat(options.brandOverride()).isEmpty();
        assertThat(options.brandOverrides()).isEmpty();
        assertThat(options.brandOverridesByItemId()).isEmpty();
        assertThat(options.itemOrder()).containsExactly("A");
    }

    @Test
    void shouldIgnoreUnsupportedRawMapAndListValues() {
        PrintItemOptions options = PrintItemOptions.from(Map.of(
                "brandOverrides", "invalid",
                "brandOverridesByItemId", List.of("invalid"),
                "itemOrder", "invalid"
        ));

        assertThat(options.brandOverride()).isEmpty();
        assertThat(options.brandOverrides()).isEmpty();
        assertThat(options.brandOverridesByItemId()).isEmpty();
        assertThat(options.itemOrder()).isEmpty();
    }

    @Test
    void shouldReturnExistingRenderOptionsAndExposeItemOptions() {
        PrintRenderOptions original = new PrintRenderOptions(
                true,
                false,
                " 沙钢 ",
                Map.of(" 原品牌 ", " 新品牌 "),
                Map.of(" 99 ", " 特殊品牌 "),
                List.of("99", "99", "88")
        );

        PrintRenderOptions parsed = PrintRenderOptions.from(original);
        PrintItemOptions itemOptions = parsed.itemOptions();

        assertThat(parsed).isSameAs(original);
        assertThat(parsed.brandOverride()).isEqualTo("沙钢");
        assertThat(itemOptions.brandOverrides()).containsOnly(Map.entry("原品牌", "新品牌"));
        assertThat(itemOptions.brandOverridesByItemId()).containsOnly(Map.entry("99", "特殊品牌"));
        assertThat(itemOptions.itemOrder()).containsExactly("99", "88");
    }

    @Test
    void shouldParseRenderOptionsFromMapAndDefaultUnsupportedValue() {
        PrintRenderOptions options = PrintRenderOptions.from(Map.of(
                "hideUnitPrice", true,
                "hideRemark", false,
                "brandOverride", " 永钢 ",
                "brandOverrides", Map.of("旧", "新"),
                "brandOverridesByItemId", Map.of("1", "特殊"),
                "itemOrder", List.of("2", "1")
        ));

        assertThat(options.hideUnitPrice()).isTrue();
        assertThat(options.hideRemark()).isFalse();
        assertThat(options.brandOverride()).isEqualTo("永钢");
        assertThat(options.brandOverrides()).containsOnly(Map.entry("旧", "新"));
        assertThat(options.brandOverridesByItemId()).containsOnly(Map.entry("1", "特殊"));
        assertThat(options.itemOrder()).containsExactly("2", "1");
        assertThat(PrintRenderOptions.from(123)).isEqualTo(PrintRenderOptions.defaults());
    }
}
