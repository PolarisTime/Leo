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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 「有效品牌集合」口径测试（口径 B：项目资料优先，空值回退到比价配置）。
 *
 * <p>这组用例锁的是**唯一读出口**的三条不变量：① 资料有品牌就不再碰配置（避免两处事实打架）；
 * ② 资料为空/NULL 时才回退且 {@code fallback=true}（前端据此提示）；
 * ③ 占位品牌「基准价」任何来源都不出现在结果里。</p>
 */
@ExtendWith(MockitoExtension.class)
class QuoteProjectEffectiveBrandsServiceTest {

    private static final Long PROJECT_ID = 88L;

    @Mock
    private ProjectProcurementScopeQuery procurementScopeQuery;

    @Mock
    private QuoteProjectConfigStore quoteProjectConfigStore;

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
    void shouldUseProjectMasterBrandsWhenPresent() {
        givenProjectBrands(List.of("中天", "萍钢"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).containsExactly("中天", "萍钢");
        assertThat(result.fallback()).isFalse();
        // 资料有清单时必须完全不读配置：否则两处品牌集合会互相污染
        verifyNoInteractions(quoteProjectConfigStore);
    }

    @Test
    void shouldFallbackToQuoteConfigWhenProjectBrandsEmpty() {
        givenProjectBrands(List.of());
        when(quoteProjectConfigStore.brandNamesOf(PROJECT_ID)).thenReturn(List.of("中杭", "亚新"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).containsExactly("中杭", "亚新");
        assertThat(result.fallback()).isTrue();
    }

    @Test
    void shouldFallbackWhenProjectBrandsIsNull() {
        // md_project.allowed_brands 可空；ProjectProcurementScope 的紧凑构造会把 null 规整成空列表
        givenProjectBrands(null);
        when(quoteProjectConfigStore.brandNamesOf(PROJECT_ID)).thenReturn(List.of("万泰"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).containsExactly("万泰");
        assertThat(result.fallback()).isTrue();
    }

    @Test
    void shouldFallbackWithoutConfigWhenProjectMissing() {
        // 项目不存在/已删除：既无资料限制也无配置 → 空集合，但仍标记为回退
        when(procurementScopeQuery.findProcurementScopeById(PROJECT_ID)).thenReturn(Optional.empty());
        when(quoteProjectConfigStore.brandNamesOf(PROJECT_ID)).thenReturn(List.of());

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).isEmpty();
        assertThat(result.fallback()).isTrue();
    }

    @Test
    void shouldReturnEmptyFallbackWhenProjectIdNull() {
        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(null);

        assertThat(result.brands()).isEmpty();
        assertThat(result.fallback()).isTrue();
        verifyNoInteractions(procurementScopeQuery, quoteProjectConfigStore);
    }

    @Test
    void shouldExcludeBaselinePricePlaceholderFromConfigFallback() {
        givenProjectBrands(List.of());
        // 「基准价」在 md_material.brand 里不存在，是历史占位行，不能作为可报价品牌下发
        when(quoteProjectConfigStore.brandNamesOf(PROJECT_ID))
                .thenReturn(List.of("基准价", "新梅鹿"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).containsExactly("新梅鹿");
        assertThat(result.fallback()).isTrue();
    }

    @Test
    void shouldExcludeBaselinePricePlaceholderFromProjectBrands() {
        givenProjectBrands(List.of("基准价", "中天"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).containsExactly("中天");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void shouldTrimDropBlanksAndDedupeWhileKeepingOrder() {
        givenProjectBrands(Arrays.asList(" 中天 ", "", "   ", "萍钢", "中天"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        // 保持原顺序去重：前端品牌列顺序 = 项目资料里的勾选顺序，不能被排序打乱
        assertThat(result.brands()).containsExactly("中天", "萍钢");
    }

    @Test
    void shouldTreatBlankOnlyProjectBrandsAsUnrestricted() {
        // 全是空白等价于「没配」→ 必须回退，不能当成「限制为空品牌」
        givenProjectBrands(Arrays.asList("", "  "));
        when(quoteProjectConfigStore.brandNamesOf(PROJECT_ID)).thenReturn(List.of("永钢"));

        QuoteProjectEffectiveBrandsResponse result = service.effectiveBrands(PROJECT_ID);

        assertThat(result.brands()).containsExactly("永钢");
        assertThat(result.fallback()).isTrue();
    }
}
