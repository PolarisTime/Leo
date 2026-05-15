package com.leo.erp.master.project.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
        @NotBlank(message = "项目编码不能为空")
        String projectCode,
        @NotBlank(message = "项目名称不能为空")
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        String projectManager,
        @NotBlank(message = "客户编码不能为空")
        String customerCode,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
