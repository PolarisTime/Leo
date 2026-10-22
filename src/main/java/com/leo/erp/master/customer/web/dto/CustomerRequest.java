package com.leo.erp.master.customer.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "客户编码不能为空")
        @Size(max = 64, message = "客户编码长度不能超过64")
        String customerCode,
        @NotBlank(message = "客户名称不能为空")
        @Size(max = 128, message = "客户名称长度不能超过128")
        String customerName,
        @Size(max = 32, message = "联系人长度不能超过32")
        String contactName,
        @Size(max = 20, message = "联系电话长度不能超过20")
        String contactPhone,
        @Size(max = 64, message = "城市长度不能超过64")
        String city,
        @Size(max = 32, message = "结算方式长度不能超过32")
        String settlementMode,
        @Size(max = 200, message = "项目名称长度不能超过200")
        String projectName,
        @Size(max = 64, message = "项目简称长度不能超过64")
        String projectNameAbbr,
        @Size(max = 255, message = "项目地址长度不能超过255")
        String projectAddress,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        @Size(max = 16, message = "状态长度不能超过16")
        String status,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
