package com.leo.erp.system.company.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CompanySettingRequest(
        @NotBlank(message = "结算主体名称不能为空")
        String companyName,
        @NotBlank(message = "税号不能为空")
        String taxNo,
        @Valid
        List<CompanySettlementAccountRequest> settlementAccounts,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
