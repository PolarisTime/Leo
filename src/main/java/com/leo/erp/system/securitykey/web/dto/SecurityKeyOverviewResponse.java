package com.leo.erp.system.securitykey.web.dto;

public record SecurityKeyOverviewResponse(
        SecurityKeyItemResponse jwt,
        SecurityKeyItemResponse totp
) {
}
