package com.leo.erp.system.setup.web.dto;

public record InitialSetupStatusResponse(
        boolean setupRequired,
        boolean adminConfigured,
        boolean companyConfigured
) {
}
