package com.leo.erp.system.setup.web.dto;

import jakarta.validation.constraints.Size;

public record InitialSetupCompanyRequest(
        @Size(max = 128)
        String companyName,
        @Size(max = 64)
        String taxNo,
        @Size(max = 128)
        String bankName,
        @Size(max = 64)
        String bankAccount,
        @Size(max = 255)
        String remark
) {
}
