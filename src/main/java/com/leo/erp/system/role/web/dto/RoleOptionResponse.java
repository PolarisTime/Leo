package com.leo.erp.system.role.web.dto;

import java.util.List;

public record RoleOptionResponse(
        Long id,
        String roleCode,
        String roleName,
        String status,
        String permissionSummary,
        List<Long> conflictRoleIds,
        boolean assignable
) {
}
