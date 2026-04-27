package com.leo.erp.master.settlementaccount.web.dto;

public record SettlementAccountResponse(
        Long id,
        String accountName,
        String companyName,
        String bankName,
        String bankAccount,
        String usageType,
        String status,
        String remark
) {
}
