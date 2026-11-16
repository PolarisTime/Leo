package com.leo.erp.master.project.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProjectOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String label,
        @JsonSerialize(using = ToStringSerializer.class) Long value,
        @JsonSerialize(using = ToStringSerializer.class) Long customerId,
        String customerCode,
        String projectCode,
        String projectName,
        String projectNameAbbr,
        @JsonSerialize(using = ToStringSerializer.class) Long settlementCompanyId,
        String settlementCompanyName,
        /** 网价浮动方向: ADD加价/SUBTRACT减价; 空表示不浮动。 */
        String priceFloatMode,
        /** 网价固定浮动幅度(元/吨), 非负。 */
        BigDecimal priceFloatValue,
        /** 项目上次交付核定使用的价格规定ID(用于默认选中)。 */
        @JsonSerialize(using = ToStringSerializer.class) Long lastPriceRuleId,
        /** 默认取价数据源: MYSTEEL/STEELX。 */
        String quoteSource,
        /** 默认取价地区(西本城市中文名)。 */
        String quoteRegion,
        /** 允许的品牌列表; 空列表表示不限制。 */
        List<String> allowedBrands,
        /** 允许的材质列表; 空列表表示不限制。 */
        List<String> allowedMaterials,
        /** 允许品牌不匹配时的处理方式: OFF/WARN/BLOCK; 清单为空时为空。 */
        String allowedBrandSeverity,
        /** 允许材质不匹配时的处理方式: OFF/WARN/BLOCK; 清单为空时为空。 */
        String allowedMaterialSeverity,
        /** 参与报单比价的商品键列表(类别|材质|规格|长度); 空列表表示全部商品可选。 */
        List<String> quoteProducts,
        /** 品牌 → 启用品种; 缺键或空列表表示该品牌全部品种。 */
        Map<String, List<String>> quoteBrandCategories,
        /** 12米定尺加价(元/吨); 空表示未配置。 */
        BigDecimal quoteLengthPremium,
        /** 是否允许 HRB400 商品按 HRB400E 价格代取。 */
        boolean quoteHrb400eFallback,
        /** 指定品牌列表(仅报单比价展示徽标)。 */
        List<String> quoteDesignatedBrands
) {

    public ProjectOptionResponse(Long id,
                                 String label,
                                 Long value,
                                 Long customerId,
                                 String customerCode,
                                 String projectCode,
                                 String projectName,
                                 String projectNameAbbr) {
        this(id, label, value, customerId, customerCode, projectCode, projectName,
                projectNameAbbr, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, false, null);
    }

    public ProjectOptionResponse(Long id,
                                 String label,
                                 Long value,
                                 Long customerId,
                                 String customerCode,
                                 String projectCode,
                                 String projectName,
                                 String projectNameAbbr,
                                 Long settlementCompanyId,
                                 String settlementCompanyName) {
        this(id, label, value, customerId, customerCode, projectCode, projectName,
                projectNameAbbr, settlementCompanyId, settlementCompanyName,
                null, null, null, null, null, null, null, null, null,
                null, null, null, false, null);
    }
}
