package com.leo.erp.system.runtimeconfig.service;

import com.leo.erp.common.support.QuoteRegionCatalog;
import com.leo.erp.common.web.PageQuerySettings;
import com.leo.erp.system.runtimeconfig.feature.FeatureFlagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 取价地区随运行时配置动态下发:
 * 前端「项目资料 / 行情同步」的地区下拉必须来自后端配置, 因此这里覆盖顺序保持与
 * 显式清空下发空列表(由前端再兜底)两类边界。
 *
 * <p>系统模块只依赖 {@link QuoteRegionCatalog} 端口, 地区默认值/顺序由行情模块实现负责,
 * 对应断言见 {@code com.leo.erp.market.steelx.SteelxPropertiesTest}。</p>
 */
class RuntimeConfigServiceTest {

    private RuntimeConfigService serviceWith(List<String> quoteRegions) {
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        PageQuerySettings pageQuerySettings = mock(PageQuerySettings.class);
        when(pageQuerySettings.getDefaultListPageSize()).thenReturn(20);
        QuoteRegionCatalog quoteRegionCatalog = () -> quoteRegions;
        return new RuntimeConfigService(featureFlagService, pageQuerySettings, quoteRegionCatalog, null);
    }

    @Test
    void businessConfig_shouldExposeConfiguredQuoteRegionsInOrder() {
        var business = serviceWith(List.of("南京", "杭州")).getRuntimeConfig().business();

        assertThat(business.quoteRegions()).containsExactly("南京", "杭州");
        assertThat(business.statement().customerReceiptAmountZero()).isTrue();
    }

    @Test
    void businessConfig_shouldPassThroughEmptyRegionsFromCatalog() {
        var business = serviceWith(List.of()).getRuntimeConfig().business();

        assertThat(business.quoteRegions()).isEmpty();
    }

    @Test
    void businessConfig_shouldExposeDefaultUiPageSize() {
        var config = serviceWith(List.of("杭州")).getRuntimeConfig();

        assertThat(config.ui().defaultPageSize()).isEqualTo(20);
    }
}
