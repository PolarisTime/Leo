package com.leo.erp.system.role.web.dto;

import java.util.List;

public record RoleSettingResponse(
        Long id,
        String roleCode,
        String roleName,
        String roleType,
        String dataScope,
        List<String> permissionCodes,
        Integer permissionCount,
        String permissionSummary,
        Integer userCount,
        String status,
        String remark
) {
}
