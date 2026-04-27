package com.leo.erp.auth.web.dto;

import java.util.List;
import java.util.Map;

public record AuthUserResponse(
        Long id,
        String loginName,
        String userName,
        String roleName,
        Boolean totpEnabled,
        Boolean forceTotpSetup,
        List<ResourcePermissionResponse> permissions,
        Map<String, String> dataScopes
) {
}
