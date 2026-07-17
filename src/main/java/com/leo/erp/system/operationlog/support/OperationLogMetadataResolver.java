package com.leo.erp.system.operationlog.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class OperationLogMetadataResolver {

    public OperationLogMetadata resolveMetadata(HandlerMethod handlerMethod, HttpServletRequest request) {
        if (handlerMethod.hasMethodAnnotation(DomainEventAudited.class)) {
            return null;
        }
        OperationLoggable operationLoggable = handlerMethod.getMethodAnnotation(OperationLoggable.class);
        if (operationLoggable != null) {
            return new OperationLogMetadata(
                    operationLoggable.moduleName(),
                    operationLoggable.moduleNameField(),
                    operationLoggable.actionType(),
                    operationLoggable.businessNoFields(),
                    operationLoggable.recordIdField(),
                    operationLoggable.moduleKeyField(),
                    false
            );
        }

        if (isReadOnlyMethod(request.getMethod())) {
            return null;
        }
        AutoLogAction autoLogAction = resolveAutoLogAction(handlerMethod, request);
        if (autoLogAction == null) {
            return null;
        }
        return new OperationLogMetadata(autoLogAction.moduleName(), "", autoLogAction.actionType(), new String[0], "", "", true);
    }

    private AutoLogAction resolveAutoLogAction(HandlerMethod handlerMethod, HttpServletRequest request) {
        String controllerName = handlerMethod.getBeanType().getSimpleName().replaceFirst("Controller$", "");
        return resolveFallbackAction(controllerName, request.getMethod());
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
