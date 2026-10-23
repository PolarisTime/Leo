package com.leo.erp.market.quotation.web.dto;

import java.util.List;

/**
 * 「该项目用于报单比价的有效品牌集合」响应。
 *
 * <p>品牌事实的唯一来源是**项目资料**（{@code md_project.allowed_brands}，逗号分隔 CSV，
 * 空表示不限制）。但历史数据里有 5 个项目没在资料里配品牌、只在比价配置
 * （{@code mk_quote_project_brand}）里配了 —— 若直接按资料取，这些项目会突然变成
 * 「不限制 = 全部品牌」，运费与品种配置全部失配。故读路径采用**空值回退**口径：</p>
 * <ul>
 *   <li>项目资料有品牌 → 用资料，{@code fallback=false}；</li>
 *   <li>项目资料为空/NULL → 回退到比价配置里的品牌，{@code fallback=true}，
 *       前端据此提示「该项目未在资料限制品牌，暂用比价配置」。</li>
 * </ul>
 *
 * <p>回退是**只读**策略：不把比价配置的品牌写回 {@code allowed_brands} —— 一旦写成非空，
 * 采购/销售的品牌校验（{@code SalesOrderProjectScopeGuard}）就会开始告警，属于比价以外的
 * 跨模块副作用，用户已明确不采用（口径 B）。</p>
 *
 * @param brands   有效品牌名（已 trim、去重、剔除占位符「基准价」，保持配置内 sort_order 顺序）
 * @param fallback 是否来自比价配置的回退（true = 项目资料未限制品牌）
 */
public record QuoteProjectEffectiveBrandsResponse(
        List<String> brands,
        boolean fallback
) {
}
