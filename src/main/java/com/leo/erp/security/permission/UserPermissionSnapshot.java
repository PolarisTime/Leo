package com.leo.erp.security.permission;

import java.util.Map;
import java.util.Set;

record UserPermissionSnapshot(
        Map<String, Set<String>> permissionMap,
        Map<String, String> dataScopeByPermission
) {
}
