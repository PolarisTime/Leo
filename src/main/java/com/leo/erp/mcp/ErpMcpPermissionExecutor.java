package com.leo.erp.mcp;

import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
class ErpMcpPermissionExecutor {

    private final ModulePermissionGuard modulePermissionGuard;
    private final PermissionService permissionService;

    ErpMcpPermissionExecutor(ModulePermissionGuard modulePermissionGuard, PermissionService permissionService) {
        this.modulePermissionGuard = modulePermissionGuard;
        this.permissionService = permissionService;
    }

    <T> T read(String moduleKey, Supplier<T> action) {
        return execute(moduleKey, ResourcePermissionCatalog.READ, action);
    }

    private <T> T execute(String moduleKey, String actionCode, Supplier<T> action) {
        SecurityPrincipal principal = currentPrincipal();
        ModulePermissionGuard.PermissionCheck permission =
                modulePermissionGuard.requireResourcePermission(principal, moduleKey, actionCode);
        DataScopeContext.Context previous = DataScopeContext.current();
        String dataScope = ResourcePermissionCatalog.isBusinessResource(permission.resource())
                ? permissionService.getUserDataScope(principal.id(), permission.resource(), permission.action())
                : ResourcePermissionCatalog.SCOPE_ALL;
        try {
            DataScopeContext.set(
                    principal.id(),
                    permission.resource(),
                    dataScope,
                    permissionService.getDataScopeOwnerUserIds(principal.id(), dataScope)
            );
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        throw new org.springframework.security.access.AccessDeniedException("未登录");
    }

    private void restore(DataScopeContext.Context previous) {
        if (previous == null) {
            DataScopeContext.clear();
            return;
        }
        DataScopeContext.set(previous.userId(), previous.resource(), previous.scope(), previous.ownerUserIds());
    }
}
