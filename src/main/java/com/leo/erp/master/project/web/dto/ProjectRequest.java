package com.leo.erp.master.project.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProjectRequest(
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
        @NotBlank(message = "状态不能为空")
        String status,
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
                null, customerCode, status, remark);
    }
}
