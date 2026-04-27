package com.leo.erp.system.company.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanySettlementAccountRequest(
        Long id,
        @NotBlank(message = "账户名称不能为空")
        String accountName,
        @NotBlank(message = "开户银行不能为空")
        String bankName,
        @NotBlank(message = "银行账号不能为空")
        String bankAccount,
        @NotBlank(message = "用途不能为空")
        String usageType,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
