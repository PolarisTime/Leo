package com.leo.erp.market.quotation.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 比价项目级配置响应。
 *
 * <p><b>V220 起形状不变、来源变了</b>（前端无需改动）：</p>
 * <ul>
 *   <li>{@code brands} 的品牌与顺序 = 项目资料「允许的品牌」（{@code md_project.allowed_brands}），
 *       不是比价配置里的品牌行；{@code freight} 仍来自 {@code mk_quote_project_brand}，
 *       未配置运费的品牌给 0；</li>
 *   <li>{@code brands[].categories} = 项目资料 {@code quote_brand_categories}，缺键 = 全部品种（空列表）；</li>
 *   <li>{@code lengthPremium}/{@code products}/{@code designatedBrands}/{@code hrb400eFallback}
 *       来自项目资料 {@code md_project.quote_*}；{@code lengthPremium} 未配置时回退定尺主数据默认值；</li>
 *   <li>{@code version}/{@code remark} 仍属于比价配置（运费编辑的乐观锁载体）。</li>
 * </ul>
 */
public record QuoteProjectConfigResponse(
        Long projectId,
        BigDecimal lengthPremium,
        boolean hrb400eFallback,
        List<String> products,
        List<String> designatedBrands,
        String remark,
        List<BrandResponse> brands,
        Long version,
        /** 项目取价数据源: MYSTEEL/STEELX; STEELX 时无品牌, 前端按单组价格展示。 */
        String quoteSource
) {

    /** 兼容旧调用方: 未携带数据源。 */
    public QuoteProjectConfigResponse(Long projectId, BigDecimal lengthPremium, boolean hrb400eFallback,
                                      List<String> products, List<String> designatedBrands, String remark,
                                      List<BrandResponse> brands, Long version) {
        this(projectId, lengthPremium, hrb400eFallback, products, designatedBrands, remark, brands, version, null);
    }

    /** 以提交后回读的权威版本覆盖当前版本, 其余字段保持不变。 */
    public QuoteProjectConfigResponse withVersion(Long newVersion) {
        return new QuoteProjectConfigResponse(projectId, lengthPremium, hrb400eFallback, products,
                designatedBrands, remark, brands, newVersion, quoteSource);
    }

    public record BrandResponse(
            String brandName,
            BigDecimal freight,
            List<String> categories,
            Integer sortOrder
    ) {
    }
}
