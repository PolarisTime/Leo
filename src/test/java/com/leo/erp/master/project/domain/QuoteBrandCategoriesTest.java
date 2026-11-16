package com.leo.erp.master.project.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目资料「品牌 → 品种」JSONB 读写（V220）。
 *
 * <p>锁的是三件事：① 空值只有一种落库表示（{@code null}），空 Map / 空数组不当成配置；
 * ② 中文键与顺序原样保留（前端按项目里的品牌顺序展示）；③ 脏 JSON 不抛异常，
 * 只退化为空配置（一条坏数据不该把项目详情打成 500）。</p>
 */
class QuoteBrandCategoriesTest {

    @Test
    void write_shouldSerializeChineseKeysInInsertionOrder() {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("中天", List.of("直条", "盘螺"));
        categories.put("萍钢", List.of("直条"));

        String json = QuoteBrandCategories.write(categories);

        assertThat(json).isEqualTo("{\"中天\":[\"直条\",\"盘螺\"],\"萍钢\":[\"直条\"]}");
    }

    @Test
    void write_shouldReturnNullWhenNothingToPersist() {
        assertThat(QuoteBrandCategories.write(null)).isNull();
        assertThat(QuoteBrandCategories.write(Map.of())).isNull();
        // 值全为空的项目等于「全部品种」，不能落成 {}（那会被读成「都不启用」）
        assertThat(QuoteBrandCategories.write(Map.of("中天", List.of()))).isNull();
        assertThat(QuoteBrandCategories.write(Map.of(" ", List.of("直条")))).isNull();
    }

    @Test
    void write_shouldTrimAndDeduplicate() {
        Map<String, List<String>> categories = Map.of(" 中天 ", Arrays.asList(" 直条 ", "直条", "", null));

        assertThat(QuoteBrandCategories.write(categories)).isEqualTo("{\"中天\":[\"直条\"]}");
    }

    @Test
    void read_shouldParseRoundTrip() {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("中天", List.of("直条", "盘螺"));

        assertThat(QuoteBrandCategories.read(QuoteBrandCategories.write(categories)))
                .containsExactly(Map.entry("中天", List.of("直条", "盘螺")));
    }

    @Test
    void read_shouldReturnEmptyMapForNullBlankOrBrokenJson() {
        assertThat(QuoteBrandCategories.read(null)).isEmpty();
        assertThat(QuoteBrandCategories.read("  ")).isEmpty();
        assertThat(QuoteBrandCategories.read("{}")).isEmpty();
        assertThat(QuoteBrandCategories.read("not-a-json")).isEmpty();
        // 结构合法但值类型不对：同样不该抛
        assertThat(QuoteBrandCategories.read("{\"中天\":\"直条\"}")).isEmpty();
    }

    @Test
    void read_shouldDropEmptyValuesAndBlankKeys() {
        assertThat(QuoteBrandCategories.read("{\"中天\":[\"直条\",\"\"],\" \":[\"盘螺\"],\"萍钢\":[]}"))
                .containsExactly(Map.entry("中天", List.of("直条")));
    }
}
