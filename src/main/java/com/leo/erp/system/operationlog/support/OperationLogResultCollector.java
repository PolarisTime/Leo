package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.common.support.ModuleCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class OperationLogResultCollector {

    public static final String BUSINESS_NO_ATTRIBUTE = OperationLogResultCollector.class.getName() + ".businessNo";
    public static final String RECORD_ID_ATTRIBUTE = OperationLogResultCollector.class.getName() + ".recordId";
    public static final String MODULE_KEY_ATTRIBUTE = OperationLogResultCollector.class.getName() + ".moduleKey";

    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final ModuleCatalog moduleCatalog;

    public OperationLogResultCollector(ObjectMapper objectMapper, ClientIpResolver clientIpResolver, ModuleCatalog moduleCatalog) {
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.moduleCatalog = moduleCatalog;
    }

    public String resolveResultStatus(int responseStatus, Exception ex) {
        if (ex != null || responseStatus >= 400) {
            return "失败";
        }
        return "成功";
    }

    public String resolveBusinessNo(HttpServletRequest request, OperationLogMetadata metadata) {
        String fromAttribute = readStringAttribute(request, BUSINESS_NO_ATTRIBUTE);
        if (fromAttribute != null) {
            return fromAttribute;
        }
        String fromRequest = joinValues(metadata.businessNoFields(), parseRequestBody(request));
        if (fromRequest != null) {
            return fromRequest;
        }

        String fromRecordId = resolveLogValue(request, metadata.recordIdField());
        if (fromRecordId != null) {
            return fromRecordId;
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

    public Long resolveRecordId(HttpServletRequest request, OperationLogMetadata metadata) {
        Long fromAttribute = readLongAttribute(request, RECORD_ID_ATTRIBUTE);
        if (fromAttribute != null) {
            return fromAttribute;
        }
        String value = resolveLogValue(request, metadata.recordIdField());
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String resolveModuleKey(HttpServletRequest request, OperationLogMetadata metadata) {
        String fromAttribute = readStringAttribute(request, MODULE_KEY_ATTRIBUTE);
        if (fromAttribute != null) {
            return fromAttribute;
        }
        return resolveLogValue(request, metadata.moduleKeyField());
    }

    public String resolveModuleName(HttpServletRequest request, OperationLogMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        String moduleNameField = metadata.moduleNameField();
        if (moduleNameField == null || moduleNameField.isBlank()) {
            return metadata.moduleName();
        }
        String moduleKey = readValue(parseRequestBody(request), moduleNameField);
        if (moduleKey == null) {
            return metadata.moduleName();
        }
        return moduleCatalog == null ? moduleKey : moduleCatalog.resolveModuleName(moduleKey);
    }

    private String resolveLogValue(HttpServletRequest request, String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        String fromRequest = readValue(parseRequestBody(request), field);
        if (fromRequest != null) {
            return fromRequest;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> uriVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return uriVariables == null ? null : trimToNull(uriVariables.get(field));
    }

    private Long readLongAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value == null ? null : String.valueOf(value).trim();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String readStringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    public String resolveRemark(Exception ex) {
        return ex == null ? null : "请求处理失败";
    }

    public String resolveRequestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        return request.getRequestURI();
    }

    public String resolveIp(HttpServletRequest request) {
        return clientIpResolver.resolveClientIpOrUnknown(request);
    }

    private JsonNode parseRequestBody(HttpServletRequest request) {
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

    private String joinValues(String[] fields, JsonNode source) {
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

    private String readValue(JsonNode source, String field) {
        if (source == null || field == null || field.isBlank()) {
            return null;
        }
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
