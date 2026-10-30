package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.api.LengthSpecCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrintSpecLengthResolver 极端情况测试：后缀来自定尺主数据（12米 → *12、9米 无后缀），
 * 覆盖追加、幂等、数字归一、空后缀、空值，以及主数据为空时的迁移兜底。
 */
class PrintSpecLengthResolverTest {

    /** 与 V202 种子一致：12米 → *12，9米 无后缀。 */
    private static final LengthSpecCatalog SEEDED = catalog(
            List.of("9米", "12米"),
            Map.of("12米", "*12"));

    @Test
    void apply_shouldAppendSuffixFromMasterData() {
        assertThat(new PrintSpecLengthResolver(SEEDED).apply("HRB400", "12米")).isEqualTo("HRB400*12");
    }

    @Test
    void apply_shouldKeepSpecForLengthWithoutSuffix() {
        // 9 米在定尺主数据里没有后缀 → 不追加
        assertThat(new PrintSpecLengthResolver(SEEDED).apply("HRB400", "9米")).isEqualTo("HRB400");
        // 未维护的长度（如 6米）同样不追加
        assertThat(new PrintSpecLengthResolver(SEEDED).apply("HRB400", "6米")).isEqualTo("HRB400");
    }

    @Test
    void apply_shouldNotDuplicateSuffix() {
        assertThat(new PrintSpecLengthResolver(SEEDED).apply("HRB400*12", "12米")).isEqualTo("HRB400*12");
    }

    @Test
    void apply_shouldHandleBlankAndNull() {
        PrintSpecLengthResolver resolver = new PrintSpecLengthResolver(SEEDED);
        assertThat(resolver.apply(null, "12米")).isEmpty();
        assertThat(resolver.apply("", "12米")).isEmpty();
        assertThat(resolver.apply("HRB400", null)).isEqualTo("HRB400");
        assertThat(resolver.apply(null, null)).isEmpty();
    }

    @Test
    void apply_shouldTrimInputs() {
        assertThat(new PrintSpecLengthResolver(SEEDED).apply(" HRB400 ", " 12米 ")).isEqualTo("HRB400*12");
    }

    @Test
    void apply_shouldMatchLengthByMeterNumber() {
        PrintSpecLengthResolver resolver = new PrintSpecLengthResolver(SEEDED);
        // 与行情匹配同口径：定尺名称精确匹配失败后统一比较米数
        assertThat(resolver.apply("HRB400", "12 米")).isEqualTo("HRB400*12");
        assertThat(resolver.apply("HRB400", "12M")).isEqualTo("HRB400*12");
        assertThat(resolver.apply("HRB400", "12")).isEqualTo("HRB400*12");
        // 米数不同则不追加
        assertThat(resolver.apply("HRB400", "112米")).isEqualTo("HRB400");
        assertThat(resolver.apply("HRB400", "-")).isEqualTo("HRB400");
    }

    @Test
    void apply_shouldAppendConfiguredSuffixForNewLength() {
        // 新增 10米 定尺并配置后缀后，无需改代码即可生效
        PrintSpecLengthResolver resolver = new PrintSpecLengthResolver(catalog(
                List.of("9米", "10米", "12米"),
                Map.of("10米", "*10", "12米", "*12")));
        assertThat(resolver.apply("HRB400", "10米")).isEqualTo("HRB400*10");
    }

    @Test
    void apply_shouldNotAppendWhenSuffixConfiguredEmpty() {
        // 主数据有定尺但没有后缀（全部清空）→ 一个都不追加，不能回落到旧写死规则
        PrintSpecLengthResolver resolver = new PrintSpecLengthResolver(catalog(
                List.of("9米", "12米"), Map.of()));
        assertThat(resolver.apply("HRB400", "12米")).isEqualTo("HRB400");
    }

    @Test
    void apply_shouldFallbackToLegacyRuleWhenMasterDataEmpty() {
        // 迁移兜底：定尺主数据为空（未初始化/回滚旧库）时保持改造前行为
        PrintSpecLengthResolver resolver = new PrintSpecLengthResolver(catalog(List.of(), Map.of()));
        assertThat(resolver.apply("HRB400", "12米")).isEqualTo("HRB400*12");
        assertThat(resolver.apply("HRB400*12", "12米")).isEqualTo("HRB400*12");
        assertThat(resolver.apply("HRB400", "9米")).isEqualTo("HRB400");
    }

    private static LengthSpecCatalog catalog(List<String> names, Map<String, String> suffixes) {
        return new LengthSpecCatalog() {
            @Override
            public Set<String> activeLengthNames() {
                return Set.copyOf(names);
            }

            @Override
            public Map<String, String> activeSpecSuffixMap() {
                return suffixes;
            }
        };
    }
}
