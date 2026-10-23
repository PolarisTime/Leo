package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.web.dto.QuoteProjectEffectiveBrandsResponse;
import com.leo.erp.master.api.ProjectProcurementScopeQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「该项目用于报单比价的**有效品牌集合**」—— 唯一实现处（口径 B：空值回退）。
 *
 * <p><b>为什么需要回退而不是直接读项目资料：</b>品牌事实已收归项目资料
 * （{@code md_project.allowed_brands}），但该字段「空 = 不限制」（{@code ProjectProcurementScope}
 * 的 {@code blocksBrands()/checksBrands()} 都要求清单非空才校验）。历史数据里 5 个项目
 * 没在资料里配品牌、却只在比价配置里配了 2~6 个品牌；若读路径不做回退，这些项目的比价页
 * 会从「2~6 个品牌」突变成「全部品牌」，且大部分品牌没有运费/品种配置。</p>
 *
 * <p><b>为什么回退是只读的：</b>把配置品牌写回 {@code allowed_brands} 会让该字段变非空，
 * 从而点亮采购/销售的品牌校验（{@code SalesOrderProjectScopeGuard} 等），这是比价以外的
 * 跨模块行为变更。用户明确选择「不要触发校验」，因此本类**只读不写**。</p>
 *
 * <p><b>「基准价」为什么在规则里排除而不是删数据：</b>它是历史配置里的占位行
 * （{@code md_material.brand} 中不存在该品牌），但它参与既有报单比价的展示；直接删配置行
 * 不可逆、也会改变已出报价单的渲染，代价远大于收益。在唯一读出口过滤，既能保证前端拿到的
 * 品牌都可报价，又保留原始数据可回溯。</p>
 */
@Service
public class QuoteProjectEffectiveBrandsService {

    /**
     * 历史占位品牌：不是真实品牌（{@code md_material.brand} 无此值），只用于比价基准价展示。
     * 任何来源（项目资料 / 比价配置）出现它都从有效品牌集合中剔除。
     */
    static final String BASELINE_PRICE_PLACEHOLDER_BRAND = "基准价";

    private final ProjectProcurementScopeQuery procurementScopeQuery;
    private final QuoteProjectConfigStore quoteProjectConfigStore;

    public QuoteProjectEffectiveBrandsService(ProjectProcurementScopeQuery procurementScopeQuery,
                                              QuoteProjectConfigStore quoteProjectConfigStore) {
        this.procurementScopeQuery = procurementScopeQuery;
        this.quoteProjectConfigStore = quoteProjectConfigStore;
    }

    /**
     * 解析项目的有效品牌集合。
     *
     * @param projectId 项目 id；为 null 时视为「无资料限制且无配置」→ 空集合 + fallback=true
     * @return 有效品牌 + 是否走了回退
     */
    public QuoteProjectEffectiveBrandsResponse effectiveBrands(Long projectId) {
        if (projectId == null) {
            return new QuoteProjectEffectiveBrandsResponse(List.of(), true);
        }
        List<String> projectBrands = procurementScopeQuery.findProcurementScopeById(projectId)
                .map(ProjectProcurementScopeQuery.ProjectProcurementScope::allowedBrands)
                .orElse(List.of());
        List<String> normalizedProjectBrands = normalize(projectBrands);
        if (!normalizedProjectBrands.isEmpty()) {
            return new QuoteProjectEffectiveBrandsResponse(normalizedProjectBrands, false);
        }
        // 项目资料为空/NULL = 不限制 → 回退到比价配置的品牌（只读，不写回 allowed_brands）
        return new QuoteProjectEffectiveBrandsResponse(
                normalize(quoteProjectConfigStore.brandNamesOf(projectId)),
                true
        );
    }

    /** trim、丢空、剔除占位符、按首次出现去重（保持配置的 sort_order 顺序）。 */
    private static List<String> normalize(List<String> brands) {
        if (brands == null || brands.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String brand : brands) {
            if (brand == null) {
                continue;
            }
            String trimmed = brand.trim();
            if (trimmed.isEmpty() || BASELINE_PRICE_PLACEHOLDER_BRAND.equals(trimmed)) {
                continue;
            }
            seen.add(trimmed);
        }
        return seen.isEmpty() ? List.of() : List.copyOf(new ArrayList<>(seen));
    }
}
