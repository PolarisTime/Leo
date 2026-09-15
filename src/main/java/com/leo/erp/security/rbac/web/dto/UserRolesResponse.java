package com.leo.erp.security.rbac.web.dto;

import java.util.List;

public record UserRolesResponse(
        Long userId,
        List<RoleResponse> roles
) {
}
