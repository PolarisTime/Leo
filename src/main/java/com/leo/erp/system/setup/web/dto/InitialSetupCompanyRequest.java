package com.leo.erp.system.setup.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InitialSetupCompanyRequest(
        @Size(max = 128)
        String companyName,
        @Size(max = 64)
        String taxNo,
        @Size(max = 128)
        String bankName,
        @Size(max = 64)
        String bankAccount,
        @DecimalMin(value = "0.0000", message = "税率不能小于0")
        BigDecimal taxRate,
        @Size(max = 16)
        String status,
        @Size(max = 255)
        String remark
) {
}
