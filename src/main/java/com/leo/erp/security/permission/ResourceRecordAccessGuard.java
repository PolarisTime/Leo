package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AuditableEntity;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ResourceRecordAccessGuard {

    private final ModulePermissionGuard modulePermissionGuard;
    private final PermissionService permissionService;

    public ResourceRecordAccessGuard(ModulePermissionGuard modulePermissionGuard,
                                     PermissionService permissionService) {
        this.modulePermissionGuard = modulePermissionGuard;
        this.permissionService = permissionService;
    }

    public void assertCurrentUserCanAccess(String moduleKey, String actionCode, AuditableEntity entity) {
        assertCanAccess(currentPrincipal(), moduleKey, actionCode, entity);
    }

    public void assertCanAccess(SecurityPrincipal principal, String moduleKey, String actionCode, AuditableEntity entity) {
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
        ModulePermissionGuard.PermissionCheck permission =
                modulePermissionGuard.requireResourcePermission(principal, moduleKey, actionCode);
        DataScopeContext.Context previous = DataScopeContext.current();
        String dataScope = permissionService.getUserDataScope(principal.id(), permission.resource(), permission.action());
        try {
            DataScopeContext.set(
                    principal.id(),
                    permission.resource(),
                    dataScope,
                    permissionService.getDataScopeOwnerUserIds(principal.id(), dataScope)
            );
            DataScopeContext.assertCanAccess(entity);
        } finally {
            restore(previous);
        }
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
    }

    private void restore(DataScopeContext.Context previous) {
        if (previous == null) {
            DataScopeContext.clear();
            return;
        }
        DataScopeContext.set(previous.userId(), previous.resource(), previous.scope(), previous.ownerUserIds());
    }
}
