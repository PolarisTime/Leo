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

    public record BrandResponse(
            String brandName,
            BigDecimal freight,
            List<String> categories,
            Integer sortOrder
    ) {
    }
}
