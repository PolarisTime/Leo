package com.leo.erp.master.customer.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CustomerRequest(
        String customerCode,
        @NotBlank(message = "客户名称不能为空")
        String customerName,
        String contactName,
        String contactPhone,
        String city,
        String settlementMode,
        @NotBlank(message = "项目名称不能为空")
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        @NotNull(message = "默认结算主体不能为空")
        Long defaultSettlementCompanyId,
        @NotBlank(message = "状态不能为空")
        String status,
        String remark
) {
}
