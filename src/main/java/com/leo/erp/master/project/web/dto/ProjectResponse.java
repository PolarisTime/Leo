package com.leo.erp.master.project.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProjectResponse(
        Long id,
        String projectCode,
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        String projectManager,
        Long customerId,
        String customerCode,
        Long settlementCompanyId,
        String settlementCompanyName,
        String status,
        String priceFloatMode,
        BigDecimal priceFloatValue,
        String quoteSource,
        String quoteRegion,
        /** 允许的品牌列表; 空列表表示不限制。 */
        List<String> allowedBrands,
        /** 允许的材质列表; 空列表表示不限制。 */
        List<String> allowedMaterials,
        /** 允许品牌不匹配时的处理方式: OFF/WARN/BLOCK; 清单为空时为空。 */
        String allowedBrandSeverity,
        /** 允许材质不匹配时的处理方式: OFF/WARN/BLOCK; 清单为空时为空。 */
        String allowedMaterialSeverity,
        /** 取价基准: DELIVERY送货日期当天/ORDER报单日期之后第N个交易日/MANUAL每次手工选择。 */
        String priceDateBasis,
        /** 报单日基准的偏移交易日数(0..30), 仅 ORDER 有意义。 */
        Integer priceDateOffsetDays,
        /** 报单日为休市日时是否按前一个交易日结算, 仅 ORDER 有意义。 */
        Boolean priceClosedDayPrevSettle,
        /** 时段策略: AUTO当日最新/FIXED固定时段/MANUAL每次手工选择。 */
        String pricePeriodPolicy,
        /** 固定取价时段(上午/中午/下午), 仅 FIXED 时有值。 */
        String pricePeriodFixed,
        String remark
) {
    public ProjectResponse(Long id,
                           String projectCode,
                           String projectName,
                           String projectNameAbbr,
                           String projectAddress,
                           String projectManager,
                           String customerCode,
                           String status,
                           String remark) {
        this(id, projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                null, customerCode, null, null, status, null, null, null, null, null, null,
                null, null, null, null, null, null, null, remark);
    }
}
