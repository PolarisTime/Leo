package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

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
        return requireResourcePermissionAny(principal, moduleKey, actionCode);
    }

    public PermissionCheck requireResourcePermissionAny(SecurityPrincipal principal, String moduleKey, String... actionCodes) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        String normalizedModuleKey = moduleKey == null ? "" : moduleKey.trim();
        if (normalizedModuleKey.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        String resource = ResourcePermissionCatalog.resolveResourceByMenuCode(normalizedModuleKey)
                .orElseGet(() -> ResourcePermissionCatalog.normalizeResource(normalizedModuleKey));
        List<String> actions = Arrays.stream(actionCodes == null ? new String[0] : actionCodes)
                .map(ResourcePermissionCatalog::normalizeAction)
                .filter(action -> !action.isBlank())
                .distinct()
                .toList();
        if (actions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "权限动作配置错误");
        }
        String allowedAction = actions.stream()
                .filter(action -> permissionService.can(principal.id(), resource, action))
                .findFirst()
                .orElse(null);
        if (allowedAction == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无操作权限");
        }
        return new PermissionCheck(normalizedModuleKey, resource, allowedAction);
    }
}
