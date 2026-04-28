package com.leo.erp.system.operationlog.support;

import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.system.norule.service.SystemSwitchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class OperationLogMetadataResolver {

    private final SystemSwitchService systemSwitchService;

    public OperationLogMetadataResolver(SystemSwitchService systemSwitchService) {
        this.systemSwitchService = systemSwitchService;
    }

    public OperationLogMetadata resolveMetadata(HandlerMethod handlerMethod, HttpServletRequest request) {
        OperationLoggable operationLoggable = handlerMethod.getMethodAnnotation(OperationLoggable.class);
        if (operationLoggable != null) {
            return new OperationLogMetadata(
                    operationLoggable.moduleName(),
                    operationLoggable.actionType(),
                    operationLoggable.businessNoFields(),
                    false
            );
        }

        AutoLogAction autoLogAction = resolveAutoLogAction(handlerMethod, request);
        if (autoLogAction == null) {
            return null;
        }

        if (systemSwitchService.shouldRecordDetailedPageActions()) {
            if (!systemSwitchService.shouldRecordDetailedPageAction(autoLogAction.key())) {
                return null;
            }
            return new OperationLogMetadata(autoLogAction.moduleName(), autoLogAction.actionType(), new String[0], true);
        }

        if (isReadOnlyMethod(request.getMethod()) || !systemSwitchService.shouldAutoRecordAllWriteOperations()) {
            return null;
        }

        return new OperationLogMetadata(autoLogAction.moduleName(), autoLogAction.actionType(), new String[0], true);
    }

    private AutoLogAction resolveAutoLogAction(HandlerMethod handlerMethod, HttpServletRequest request) {
        RequiresPermission requiresPermission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (requiresPermission == null || requiresPermission.resource().isBlank()) {
            return null;
        }

        String moduleName = ResourcePermissionCatalog.resourceTitle(requiresPermission.resource().trim());
        String action = ResourcePermissionCatalog.normalizeAction(requiresPermission.action());
        return switch (action) {
            case ResourcePermissionCatalog.READ -> resolveViewAction(moduleName, request);
            case ResourcePermissionCatalog.CREATE -> new AutoLogAction("CREATE", moduleName, "新增");
            case ResourcePermissionCatalog.UPDATE -> new AutoLogAction("EDIT", moduleName, "编辑");
            case ResourcePermissionCatalog.DELETE -> new AutoLogAction("DELETE", moduleName, "删除");
            case ResourcePermissionCatalog.AUDIT -> new AutoLogAction("AUDIT", moduleName, "审核");
            case ResourcePermissionCatalog.EXPORT -> new AutoLogAction("EXPORT", moduleName, "导出");
            case ResourcePermissionCatalog.PRINT -> new AutoLogAction("PRINT", moduleName, "打印");
            default -> resolveFallbackAction(moduleName, request.getMethod());
        };
    }

    private AutoLogAction resolveViewAction(String moduleName, HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        return hasPathVariableId(request)
                ? new AutoLogAction("DETAIL", moduleName, "查看")
                : new AutoLogAction("QUERY", moduleName, "查询");
    }

    private AutoLogAction resolveFallbackAction(String moduleName, String requestMethod) {
        return switch (requestMethod == null ? "" : requestMethod.trim().toUpperCase()) {
            case "POST" -> new AutoLogAction("CREATE", moduleName, "新增");
            case "PUT", "PATCH" -> new AutoLogAction("EDIT", moduleName, "编辑");
            case "DELETE" -> new AutoLogAction("DELETE", moduleName, "删除");
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    boolean hasPathVariableId(HttpServletRequest request) {
        Map<String, String> uriVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVariables == null) {
            return false;
        }
        String id = uriVariables.get("id");
        return id != null && !id.isBlank();
    }

    boolean isReadOnlyMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    record AutoLogAction(String key, String moduleName, String actionType) {}
}
