package com.leo.erp.market.quotation.web.dto;

import java.util.List;

/**
 * 「该项目用于报单比价的有效品牌集合」响应。
 *
 * <p>品牌事实的唯一来源是**项目资料**（{@code md_project.allowed_brands}，逗号分隔 CSV，
 * 空表示不限制）。V220 之前读路径还有一条「资料为空 → 回退到比价配置品牌」的分支；
 * 迁移脚本已把配置品牌回填进项目资料并把品牌校验力度置 {@code OFF}，回退因此删除。</p>
 *
 * <p>{@code fallback} 字段**保留但恒为 {@code false}**：删字段对已发布的响应是破坏性变更，
 * 而前端已有「该项目未在资料限制品牌」的提示逻辑，保留字段可让它平滑退化为「永不提示」，
 * 确认无消费方后再删除。</p>
 *
 * @param brands   有效品牌名（已 trim、去重、剔除占位符「基准价」，保持项目允许品牌的顺序）
 * @param fallback 历史兼容字段，恒为 false
 */
public record QuoteProjectEffectiveBrandsResponse(
        List<String> brands,
        boolean fallback
) {
}
