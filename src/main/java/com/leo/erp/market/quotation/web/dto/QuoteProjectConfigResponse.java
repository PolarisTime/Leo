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
        Long version
) {

    /** 以提交后回读的权威版本覆盖当前版本, 其余字段保持不变。 */
    public QuoteProjectConfigResponse withVersion(Long newVersion) {
        return new QuoteProjectConfigResponse(projectId, lengthPremium, hrb400eFallback, products,
                designatedBrands, remark, brands, newVersion);
    }

    public record BrandResponse(
            String brandName,
            BigDecimal freight,
            List<String> categories,
            Integer sortOrder
    ) {
    }
}
