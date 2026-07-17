package com.leo.erp.auth.web.dto;

import java.util.List;

public record AuthUserResponse(
        Long id,
        String loginName,
        String userName,
        String roleName,
        Boolean totpEnabled,
        Boolean forceTotpSetup,
        List<ResourcePermissionResponse> permissions
) {
}
