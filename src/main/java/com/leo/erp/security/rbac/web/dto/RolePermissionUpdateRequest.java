package com.leo.erp.security.rbac.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RolePermissionUpdateRequest(
        @NotNull(message = "权限集合不能为空")
        List<String> permissions
) {
}
