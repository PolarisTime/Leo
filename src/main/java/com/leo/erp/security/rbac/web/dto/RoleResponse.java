package com.leo.erp.security.rbac.web.dto;

public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean builtin,
        String status
) {
}
