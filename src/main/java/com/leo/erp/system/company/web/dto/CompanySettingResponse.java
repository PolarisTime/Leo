package com.leo.erp.system.company.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record CompanySettingResponse(
        Long id,
        String companyName,
        String taxNo,
        String bankName,
        String bankAccount,
        BigDecimal taxRate,
        List<CompanySettlementAccountResponse> settlementAccounts,
        String status,
        String remark
) {
}
