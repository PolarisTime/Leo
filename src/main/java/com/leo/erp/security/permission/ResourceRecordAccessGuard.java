package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ResourceRecordAccessGuard {

    private final ModulePermissionGuard modulePermissionGuard;
    public ResourceRecordAccessGuard(ModulePermissionGuard modulePermissionGuard) {
        this.modulePermissionGuard = modulePermissionGuard;
    }

    public void assertCurrentUserCanAccess(String moduleKey, String actionCode, AbstractAuditableEntity entity) {
        assertCanAccess(currentPrincipal(), moduleKey, actionCode, entity);
    }

    public void assertCanAccess(SecurityPrincipal principal, String moduleKey, String actionCode, AbstractAuditableEntity entity) {
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
        modulePermissionGuard.requireResourcePermission(principal, moduleKey, actionCode);
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
    }

}
