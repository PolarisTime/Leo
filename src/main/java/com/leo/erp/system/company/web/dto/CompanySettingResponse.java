package com.leo.erp.system.company.web.dto;

import java.util.List;

public record CompanySettingResponse(
        Long id,
        String companyName,
        String taxNo,
        String bankName,
        String bankAccount,
        List<CompanySettlementAccountResponse> settlementAccounts,
        String status,
        String remark
) {
}
