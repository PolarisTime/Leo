package com.leo.erp.master.customer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank(message = "客户编码不能为空")
        String customerCode,
        @NotBlank(message = "客户名称不能为空")
        String customerName,
        String contactName,
        String contactPhone,
        String city,
        String settlementMode,
        @NotBlank(message = "项目名称不能为空")
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
