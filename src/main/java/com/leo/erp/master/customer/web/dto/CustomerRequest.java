package com.leo.erp.master.customer.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CustomerRequest(
        @NotBlank(message = "客户编码不能为空")
        String customerCode,
        @NotBlank(message = "客户名称不能为空")
        String customerName,
        String contactName,
        String contactPhone,
        String city,
        String settlementMode,
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        @NotNull(message = "默认结算主体不能为空")
        @SnowflakeId Long defaultSettlementCompanyId,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        String status,
        String remark
) {
}
