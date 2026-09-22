package com.leo.erp.market.quotation.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** 比价项目级配置响应。 */
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
