package com.leo.erp.system.role.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RolePermissionItem(
        @NotBlank String resource,
        @NotBlank String action
) {
}
