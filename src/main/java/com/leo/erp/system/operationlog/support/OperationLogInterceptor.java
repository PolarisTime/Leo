package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.operationlog.service.OperationLogCommand;
import com.leo.erp.system.operationlog.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OperationLogInterceptor.class);

    static final String METADATA_ATTRIBUTE = OperationLogInterceptor.class.getName() + ".metadata";

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final ModuleCatalog moduleCatalog;
    private final SystemSwitchService systemSwitchService;

    public OperationLogInterceptor(OperationLogService operationLogService,
                                   ObjectMapper objectMapper,
                                   ModuleCatalog moduleCatalog,
                                   SystemSwitchService systemSwitchService) {
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
        this.moduleCatalog = moduleCatalog;
        this.systemSwitchService = systemSwitchService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        OperationLogMetadata metadata = resolveMetadata(handlerMethod, request);
        if (metadata == null) {
            return true;
        }

        request.setAttribute(METADATA_ATTRIBUTE, metadata);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OperationLogMetadata metadata = (OperationLogMetadata) request.getAttribute(METADATA_ATTRIBUTE);
        if (metadata == null) {
            return;
        }

        try {
            ApiResponse<?> apiResponse = extractApiResponse(request);
            String resultStatus = resolveResultStatus(apiResponse, response, ex);
            String businessNo = resolveBusinessNo(request, apiResponse, metadata);
            String remark = resolveRemark(apiResponse, ex);
            operationLogService.record(new OperationLogCommand(
                    metadata.moduleName(),
                    metadata.actionType(),
                    businessNo,
                    request.getMethod(),
                    resolveRequestPath(request),
                    resolveIp(request),
                    resultStatus,
                    remark
            ));
        } catch (Exception logEx) {
            log.warn("操作日志写入失败: {} {}", request.getMethod(), request.getRequestURI(), logEx);
        }
    }

    private ApiResponse<?> extractApiResponse(HttpServletRequest request) {
        Object body = request.getAttribute(OperationLogResponseBodyAdvice.RESPONSE_BODY_ATTRIBUTE);
        return body instanceof ApiResponse<?> apiResponse ? apiResponse : null;
    }

    private OperationLogMetadata resolveMetadata(HandlerMethod handlerMethod, HttpServletRequest request) {
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

    private String resolveResultStatus(ApiResponse<?> apiResponse, HttpServletResponse response, Exception ex) {
        if (apiResponse != null) {
            return apiResponse.code() == 0 ? "成功" : "失败";
        }
        if (ex != null || response.getStatus() >= 400) {
            return "失败";
        }
        return "成功";
    }

    private String resolveBusinessNo(HttpServletRequest request, ApiResponse<?> apiResponse, OperationLogMetadata metadata) {
        String fromResponse = joinValues(metadata.businessNoFields(), apiResponse == null ? null : apiResponse.data());
        if (fromResponse != null) {
            return fromResponse;
        }

        String fromRequest = joinValues(metadata.businessNoFields(), parseRequestBody(request));
        if (fromRequest != null) {
            return fromRequest;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> uriVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVariables != null) {
            String id = uriVariables.get("id");
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return null;
    }

    private boolean isReadOnlyMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
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
    private boolean hasPathVariableId(HttpServletRequest request) {
        Map<String, String> uriVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVariables == null) {
            return false;
        }
        String id = uriVariables.get("id");
        return id != null && !id.isBlank();
    }

    private Object parseRequestBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }

        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }

        String body = new String(content, StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String joinValues(String[] fields, Object source) {
        if (fields == null || fields.length == 0 || source == null) {
            return null;
        }

        StringJoiner joiner = new StringJoiner("/");
        for (String field : fields) {
            String value = readValue(source, field);
            if (value != null) {
                joiner.add(value);
            }
        }

        String joined = joiner.toString().trim();
        return joined.isEmpty() ? null : joined;
    }

    private String readValue(Object source, String field) {
        if (source == null || field == null || field.isBlank()) {
            return null;
        }

        if (source instanceof JsonNode jsonNode) {
            JsonNode value = jsonNode.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            String text = value.asText(null);
            return text == null || text.isBlank() ? null : text.trim();
        }

        if (source instanceof Map<?, ?> map) {
            Object value = map.get(field);
            return value == null ? null : String.valueOf(value).trim();
        }

        Object value = invokeNoArgMethod(source, field);
        if (value == null) {
            value = invokeNoArgMethod(source, "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
        }
        if (value == null) {
            value = readField(source, field);
        }
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Object invokeNoArgMethod(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            return method.invoke(source);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object readField(Object source, String fieldName) {
        Class<?> type = source.getClass();
        while (type != null && !Objects.equals(type, Object.class)) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private String resolveRemark(ApiResponse<?> apiResponse, Exception ex) {
        if (apiResponse != null && apiResponse.message() != null && !apiResponse.message().isBlank()) {
            return apiResponse.message();
        }
        return ex == null ? null : ex.getMessage();
    }

    private String resolveRequestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        return request.getRequestURI();
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record AutoLogAction(String key, String moduleName, String actionType) {
    }
}
