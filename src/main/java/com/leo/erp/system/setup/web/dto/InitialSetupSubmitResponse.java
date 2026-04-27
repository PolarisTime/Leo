package com.leo.erp.system.setup.web.dto;

public record InitialSetupSubmitResponse(
        String adminLoginName,
        String companyName
) {
}
