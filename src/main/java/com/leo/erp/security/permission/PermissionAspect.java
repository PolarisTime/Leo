package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.ApiKeyAuthenticationDetails;
import com.leo.erp.security.support.SecurityPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }

        ApiKeyAuthenticationDetails apiKeyDetails = authentication.getDetails() instanceof ApiKeyAuthenticationDetails details
                ? details
                : null;

        if (requiresPermission.authenticatedOnly()) {
            if (apiKeyDetails != null && !requiresPermission.allowApiKey()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 不允许访问该接口");
            }
            return joinPoint.proceed();
        }

        String resource = ResourcePermissionCatalog.normalizeResource(requiresPermission.resource());
        String action = ResourcePermissionCatalog.normalizeAction(requiresPermission.action());

        if (resource.isEmpty() || action.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "权限注解配置错误：resource 和 action 不能为空");
        }

        if (apiKeyDetails != null) {
            if (!apiKeyDetails.allowedResources().isEmpty() && !apiKeyDetails.allowedResources().contains(resource)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 未开通该资源接口权限");
            }
            if (apiKeyDetails.allowedActions().isEmpty()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 未配置动作权限");
            }
            if (!apiKeyDetails.allowedActions().contains(action)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 未开通该动作权限");
            }
        }

        if (!permissionService.can(principal.id(), resource, action)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无操作权限");
        }

        String dataScope = ResourcePermissionCatalog.isBusinessResource(resource)
                ? permissionService.getUserDataScope(principal.id(), resource, action)
                : ResourcePermissionCatalog.SCOPE_ALL;
        DataScopeContext.Context previous = DataScopeContext.current();
        DataScopeContext.set(principal.id(), resource, dataScope, permissionService.getDataScopeOwnerUserIds(principal.id(), dataScope));
        try {
            return joinPoint.proceed();
        } finally {
            restore(previous);
        }
    }

    private void restore(DataScopeContext.Context previous) {
        if (previous == null) {
            DataScopeContext.clear();
            return;
        }
        DataScopeContext.set(previous.userId(), previous.resource(), previous.scope(), previous.ownerUserIds());
    }
}
