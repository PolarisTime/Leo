package com.leo.erp.master.project.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProjectRequest(
        @NotBlank(message = "项目编码不能为空")
        String projectCode,
        @NotBlank(message = "项目名称不能为空")
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        String projectManager,
        @Positive(message = "客户ID必须为正整数")
        Long customerId,
        @NotBlank(message = "客户编码不能为空")
        String customerCode,
        @Positive(message = "结算主体ID必须为正整数")
        Long settlementCompanyId,
        String settlementCompanyName,
        @NotBlank(message = "状态不能为空")
        String status,
        /** 网价浮动方向: ADD加价/SUBTRACT减价; 空表示不浮动。 */
        String priceFloatMode,
        /** 网价固定浮动幅度(元/吨), 非负。 */
        @PositiveOrZero(message = "网价浮动幅度不能为负")
        BigDecimal priceFloatValue,
        String remark
) {
    public ProjectRequest(String projectCode,
                          String projectName,
                          String projectNameAbbr,
                          String projectAddress,
                          String projectManager,
                          String customerCode,
                          String status,
                          String remark) {
        this(projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                null, customerCode, null, null, status, null, null, remark);
    }

    /** 兼容旧调用方: 未携带网价浮动。 */
    public ProjectRequest(String projectCode,
                          String projectName,
                          String projectNameAbbr,
                          String projectAddress,
                          String projectManager,
                          Long customerId,
                          String customerCode,
                          Long settlementCompanyId,
                          String settlementCompanyName,
                          String status,
                          String remark) {
        this(projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                customerId, customerCode, settlementCompanyId, settlementCompanyName,
                status, null, null, remark);
    }
}
