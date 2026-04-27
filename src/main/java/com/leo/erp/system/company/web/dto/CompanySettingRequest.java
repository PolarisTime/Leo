package com.leo.erp.system.company.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CompanySettingRequest(
        @NotBlank(message = "公司名称不能为空")
        String companyName,
        @NotBlank(message = "税号不能为空")
        String taxNo,
        @Valid
        @NotEmpty(message = "至少需要维护一个结算账户")
        List<CompanySettlementAccountRequest> settlementAccounts,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
