package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.web.dto.QuoteProjectEffectiveBrandsResponse;
import com.leo.erp.master.api.ProjectProcurementScopeQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「有效品牌集合」口径测试（V220 起唯一口径：项目资料 {@code allowed_brands}）。
 *
 * <p>归并前还有「项目资料为空 → 回退到比价配置品牌 + fallback=true」的分支；品牌已由 V220 回填进
 * 项目资料，回退删除，因此这组用例锁的是：① 只读项目资料；② {@code fallback} 恒为 false
 * （字段保留只为不破坏响应形状）；③ 占位品牌「基准价」仍被防御性过滤。</p>
 */
@ExtendWith(MockitoExtension.class)
class QuoteProjectEffectiveBrandsServiceTest {

    private static final Long PROJECT_ID = 88L;

    @Mock
    private ProjectProcurementScopeQuery procurementScopeQuery;

    @InjectMocks
    private QuoteProjectEffectiveBrandsService service;

    private void givenProjectBrands(List<String> allowedBrands) {
        when(procurementScopeQuery.findProcurementScopeById(PROJECT_ID))
                .thenReturn(Optional.of(new ProjectProcurementScopeQuery.ProjectProcurementScope(
                        PROJECT_ID,
                        "测试项目",
                        allowedBrands,
                        List.of(),
                        ProjectProcurementScopeQuery.Enforcement.WARN,
                        ProjectProcurementScopeQuery.Enforcement.WARN)));
    }

    @Test
    void shouldUseProjectMasterBrands() {
        givenProjectBrands(List.of("中天", "萍钢"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).containsExactly("中天", "萍钢");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void shouldNotFallBackToQuoteConfigWhenProjectHasNoBrands() {
        // 归并后不再有回退分支：资料为空就是空集合，且 fallback 恒为 false
        givenProjectBrands(List.of());

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).isEmpty();
        assertThat(result.fallback()).isFalse();
        verify(procurementScopeQuery).findProcurementScopeById(PROJECT_ID);
    }

    @Test
    void shouldFilterBaselinePricePlaceholder() {
        // 项目范围快照本身不允许 null 元素；这里覆盖「空白/占位符/重复/前后空格」四种清洗场景
        givenProjectBrands(Arrays.asList("中天", "  ", "基准价", " 萍钢 ", "中天"));

        assertThat(service.effectiveBrands(PROJECT_ID).brands()).containsExactly("中天", "萍钢");
    }

    @Test
    void shouldReturnEmptyWhenProjectIdIsNull() {
        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(null);

        assertThat(result.brands()).isEmpty();
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenProjectMissing() {
        when(procurementScopeQuery.findProcurementScopeById(PROJECT_ID)).thenReturn(Optional.empty());

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).isEmpty();
        assertThat(result.fallback()).isFalse();
    }
}
