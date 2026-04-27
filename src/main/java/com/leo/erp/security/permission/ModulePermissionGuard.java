package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.ApiKeyAuthenticationDetails;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ModulePermissionGuard {

    private final PermissionService permissionService;

    public record PermissionCheck(String moduleKey, String resource, String action) {
    }

    public ModulePermissionGuard(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public String requirePermission(SecurityPrincipal principal, String moduleKey, String actionCode) {
        return requireResourcePermission(principal, moduleKey, actionCode).moduleKey();
    }

    public PermissionCheck requireResourcePermission(SecurityPrincipal principal, String moduleKey, String actionCode) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        String normalizedModuleKey = moduleKey == null ? "" : moduleKey.trim();
        if (normalizedModuleKey.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        String resource = ResourcePermissionCatalog.resolveResourceByMenuCode(normalizedModuleKey)
                .orElseGet(() -> ResourcePermissionCatalog.normalizeResource(normalizedModuleKey));
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof ApiKeyAuthenticationDetails details) {
            if (!details.allowedResources().isEmpty() && !details.allowedResources().contains(resource)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 未开通该资源接口权限");
            }
            if (details.allowedActions().isEmpty()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 未配置动作权限");
            }
            if (!details.allowedActions().contains(action)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 未开通该动作权限");
            }
        }
        if (!permissionService.can(principal.id(), resource, action)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无操作权限");
        }
        return new PermissionCheck(normalizedModuleKey, resource, action);
    }
}
