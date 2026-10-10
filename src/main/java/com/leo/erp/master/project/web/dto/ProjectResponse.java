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
                null, customerCode, null, null, status, null, null, null, null, null, null, remark);
    }
}
