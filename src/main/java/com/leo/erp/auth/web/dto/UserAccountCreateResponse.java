package com.leo.erp.auth.web.dto;

public record UserAccountCreateResponse(
        UserAccountAdminResponse user,
        String initialPassword
) {
}
