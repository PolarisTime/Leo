package com.leo.erp.system.company.web.dto;

public record CompanySettlementAccountRequest(
        Long id,
        String accountName,
        String bankName,
        String bankAccount,
        String usageType,
        String status,
        String remark
) {
}
