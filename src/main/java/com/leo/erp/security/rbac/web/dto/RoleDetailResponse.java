package com.leo.erp.security.rbac.web.dto;

import java.util.List;

public record RoleDetailResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean builtin,
        String status,
        long permissionCount,
        long userCount,
        List<String> permissions
) {
}
