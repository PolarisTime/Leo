package com.leo.erp.market.steelx;

import com.leo.erp.common.support.QuoteRegionCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 取价地区目录端口契约:
 * {@link SteelxProperties} 作为 {@link QuoteRegionCatalog} 的实现, 负责把抓取配置里的
 * 地区映射归一成「可选取价地区」, 供运行时配置下发; 未配置回退内置城市, 显式清空下发空列表。
 */
class SteelxPropertiesTest {

    @Test
    void listSupportedQuoteRegions_shouldFallBackToBuiltInCitiesWhenUnconfigured() {
        assertThat(new SteelxProperties().listSupportedQuoteRegions())
                .containsExactly("杭州", "上海", "宁波", "嘉兴", "绍兴");
    }

    @Test
    void listSupportedQuoteRegions_shouldKeepConfiguredOrder() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of(
                new SteelxProperties.Region("南京", "nanjing"),
                new SteelxProperties.Region("杭州", "hangzhou")));

        assertThat(properties.listSupportedQuoteRegions()).containsExactly("南京", "杭州");
    }

    @Test
    void listSupportedQuoteRegions_shouldExposeEmptyListWhenRegionsCleared() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of());

        assertThat(properties.listSupportedQuoteRegions()).isEmpty();
    }

    @Test
    void listSupportedQuoteRegions_shouldSkipIncompleteRegionEntries() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of(
                new SteelxProperties.Region("南京", "nanjing"),
                new SteelxProperties.Region(null, "shanghai"),
                new SteelxProperties.Region("宁波", null)));

        assertThat(properties.listSupportedQuoteRegions()).containsExactly("南京");
    }

    @Test
    void listSupportedQuoteRegions_shouldAgreeWithSupportedRegions() {
        SteelxProperties properties = new SteelxProperties();

        assertThat(properties.listSupportedQuoteRegions())
                .isEqualTo(properties.supportedRegions());
    }
}
