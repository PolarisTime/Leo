package com.leo.erp.mcp;

import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
class ErpMcpPermissionExecutor {

    private final ModulePermissionGuard modulePermissionGuard;
    ErpMcpPermissionExecutor(ModulePermissionGuard modulePermissionGuard) {
        this.modulePermissionGuard = modulePermissionGuard;
    }

    <T> T read(String moduleKey, Supplier<T> action) {
        return execute(moduleKey, ResourcePermissionCatalog.READ, action);
    }

    <T> T print(String moduleKey, Supplier<T> action) {
        return execute(moduleKey, ResourcePermissionCatalog.PRINT, action);
    }

    private <T> T execute(String moduleKey, String actionCode, Supplier<T> action) {
        SecurityPrincipal principal = currentPrincipal();
        modulePermissionGuard.requireResourcePermission(principal, moduleKey, actionCode);
        return action.get();
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        throw new org.springframework.security.access.AccessDeniedException("未登录");
    }

}
