package com.leo.erp.system.runtimeconfig.service;

import com.leo.erp.common.web.PageQuerySettings;
import com.leo.erp.market.steelx.SteelxProperties;
import com.leo.erp.system.runtimeconfig.feature.FeatureFlagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 取价地区随运行时配置动态下发:
 * 前端「项目资料 / 行情同步」的地区下拉必须来自后端配置, 因此这里覆盖顺序保持、
 * 未配置回退内置城市、显式清空下发空列表(由前端再兜底)三类边界。
 */
class RuntimeConfigServiceTest {

    private RuntimeConfigService serviceWith(SteelxProperties properties) {
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        PageQuerySettings pageQuerySettings = mock(PageQuerySettings.class);
        when(pageQuerySettings.getDefaultListPageSize()).thenReturn(20);
        return new RuntimeConfigService(featureFlagService, pageQuerySettings, properties, null);
    }

    @Test
    void businessConfig_shouldExposeConfiguredQuoteRegionsInOrder() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of(
                new SteelxProperties.Region("南京", "nanjing"),
                new SteelxProperties.Region("杭州", "hangzhou")));

        var business = serviceWith(properties).getRuntimeConfig().business();

        assertThat(business.quoteRegions()).containsExactly("南京", "杭州");
        assertThat(business.statement().customerReceiptAmountZero()).isTrue();
    }

    @Test
    void businessConfig_shouldFallBackToBuiltInRegionsWhenUnconfigured() {
        var business = serviceWith(new SteelxProperties()).getRuntimeConfig().business();

        assertThat(business.quoteRegions())
                .containsExactly("杭州", "上海", "宁波", "嘉兴", "绍兴");
    }

    @Test
    void businessConfig_shouldExposeEmptyListWhenRegionsCleared() {
        SteelxProperties properties = new SteelxProperties();
        properties.setRegions(List.of());

        var business = serviceWith(properties).getRuntimeConfig().business();

        assertThat(business.quoteRegions()).isEmpty();
    }
}
