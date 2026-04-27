package com.leo.erp.master.customer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank(message = "客户编码不能为空")
        String customerCode,
        @NotBlank(message = "客户名称不能为空")
        String customerName,
        @NotBlank(message = "联系人不能为空")
        String contactName,
        @NotBlank(message = "联系电话不能为空")
        String contactPhone,
        @NotBlank(message = "所在城市不能为空")
        String city,
        @NotBlank(message = "结算方式不能为空")
        String settlementMode,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
