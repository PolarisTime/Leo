package com.leo.erp.market.quotation.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 比价项目级配置保存请求(整体替换)。 */
public record QuoteProjectConfigRequest(
        @DecimalMin(value = "0", message = "12米加价不能为负") BigDecimal lengthPremium,
        Boolean hrb400eFallback,
        List<String> products,
        List<String> designatedBrands,
        @Size(max = 255, message = "备注过长") String remark,
        @Valid List<BrandRequest> brands
) {

    /** 参与品牌: 运费与启用品种。 */
    public record BrandRequest(
            @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称过长") String brandName,
            @DecimalMin(value = "0", message = "运费不能为负") BigDecimal freight,
            List<String> categories,
            Integer sortOrder
    ) {
    }
}
