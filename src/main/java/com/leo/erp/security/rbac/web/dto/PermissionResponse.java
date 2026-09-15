package com.leo.erp.security.rbac.web.dto;

public record PermissionResponse(
        String code,
        String resource,
        String action,
        String field,
        String description
) {
}
