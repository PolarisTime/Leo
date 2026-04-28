package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.IpResolutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Component
public class OperationLogResultCollector {

    private final ObjectMapper objectMapper;
    private final IpResolutionService ipResolutionService;

    public OperationLogResultCollector(ObjectMapper objectMapper, IpResolutionService ipResolutionService) {
        this.objectMapper = objectMapper;
        this.ipResolutionService = ipResolutionService;
    }

    public ApiResponse<?> extractApiResponse(HttpServletRequest request) {
        Object body = request.getAttribute(OperationLogResponseBodyAdvice.RESPONSE_BODY_ATTRIBUTE);
        return body instanceof ApiResponse<?> apiResponse ? apiResponse : null;
    }

    public String resolveResultStatus(ApiResponse<?> apiResponse, HttpServletResponse response, Exception ex) {
        if (apiResponse != null) {
            return apiResponse.code() == 0 ? "成功" : "失败";
        }
        if (ex != null || response.getStatus() >= 400) {
            return "失败";
        }
        return "成功";
    }

    public String resolveBusinessNo(HttpServletRequest request, ApiResponse<?> apiResponse, OperationLogMetadata metadata) {
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

    public String resolveRemark(ApiResponse<?> apiResponse, Exception ex) {
        if (apiResponse != null && apiResponse.message() != null && !apiResponse.message().isBlank()) {
            return apiResponse.message();
        }
        return ex == null ? null : ex.getMessage();
    }

    public String resolveRequestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        return request.getRequestURI();
    }

    public String resolveIp(HttpServletRequest request) {
        return ipResolutionService.resolveClientIpOrUnknown(request);
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
}
