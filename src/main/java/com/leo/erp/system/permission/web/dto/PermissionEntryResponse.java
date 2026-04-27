package com.leo.erp.system.permission.web.dto;

public record PermissionEntryResponse(
        Long id,
        String permissionCode,
        String permissionName,
        String moduleName,
        String permissionType,
        String actionName,
        String scopeName,
        String resourceKey,
        String status,
        String remark
) {
}
