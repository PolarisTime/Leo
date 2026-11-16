package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.web.dto.QuoteProjectEffectiveBrandsResponse;
import com.leo.erp.master.api.ProjectProcurementScopeQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「该项目用于报单比价的**有效品牌集合**」—— 唯一实现处。
 *
 * <p><b>V220 起只有一种口径</b>：读项目资料的 {@code md_project.allowed_brands}。归并前这里
 * 还有一条「项目资料为空就回退到比价配置品牌」的分支（并置 {@code fallback=true} 让前端提示），
 * 那是因为品牌事实在项目资料与比价配置两处；迁移脚本 V220 已把配置品牌回填进项目资料，
 * 回退分支因此删除，{@code fallback} 恒为 {@code false}（字段保留只为不破坏响应形状）。</p>
 *
 * <p><b>「基准价」仍在这里过滤</b>：它是历史配置里的占位行（{@code md_material.brand} 无此值），
 * 迁移已清理库内数据，但外部导入 / 人工改库仍可能带回来；读出口继续兜底，
 * 保证前端拿到的品牌都可报价。</p>
 */
@Service
public class QuoteProjectEffectiveBrandsService {

    /**
     * 历史占位品牌：不是真实品牌（{@code md_material.brand} 无此值），只用于比价基准价展示。
     * 任何来源出现它都从有效品牌集合中剔除。
     */
    static final String BASELINE_PRICE_PLACEHOLDER_BRAND = "基准价";

    private final ProjectProcurementScopeQuery procurementScopeQuery;

    public QuoteProjectEffectiveBrandsService(ProjectProcurementScopeQuery procurementScopeQuery) {
        this.procurementScopeQuery = procurementScopeQuery;
    }

    /**
     * 解析项目的有效品牌集合。
     *
     * @param projectId 项目 id；为 null 时视为「无资料」→ 空集合
     * @return 有效品牌 + {@code fallback=false}（字段保留，恒为 false）
     */
    public QuoteProjectEffectiveBrandsResponse effectiveBrands(Long projectId) {
        if (projectId == null) {
            return new QuoteProjectEffectiveBrandsResponse(List.of(), false);
        }
        List<String> projectBrands = procurementScopeQuery.findProcurementScopeById(projectId)
                .map(ProjectProcurementScopeQuery.ProjectProcurementScope::allowedBrands)
                .orElse(List.of());
        return new QuoteProjectEffectiveBrandsResponse(normalize(projectBrands), false);
    }

    /** trim、丢空、剔除占位符、按首次出现去重（保持项目允许品牌的顺序）。 */
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
