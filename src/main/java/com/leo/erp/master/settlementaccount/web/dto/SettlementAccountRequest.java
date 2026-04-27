package com.leo.erp.master.settlementaccount.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SettlementAccountRequest(
        @NotBlank(message = "结算账户名称不能为空")
        String accountName,
        @NotBlank(message = "所属公司不能为空")
        String companyName,
        @NotBlank(message = "开户银行不能为空")
        String bankName,
        @NotBlank(message = "账号不能为空")
        String bankAccount,
        @NotBlank(message = "用途不能为空")
        String usageType,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
