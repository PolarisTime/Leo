package com.leo.erp.auth.web.dto;

public record CurrentUserSecurityResponse(
        Long id,
        String loginName,
        String userName,
        Boolean totpEnabled,
        Boolean forceTotpSetup,
        Boolean forbidDisable2fa
) {
}
