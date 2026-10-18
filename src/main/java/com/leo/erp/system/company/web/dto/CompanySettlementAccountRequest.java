package com.leo.erp.system.company.web.dto;

import com.leo.erp.common.validation.SnowflakeId;

public record CompanySettlementAccountRequest(
        @SnowflakeId Long id,
        String accountName,
        String bankName,
        String bankAccount,
        String usageType,
        String status,
        String remark
) {
}
