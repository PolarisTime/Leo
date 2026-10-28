package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.common.support.ModuleCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class OperationLogResultCollector {

    public static final String BUSINESS_NO_ATTRIBUTE = OperationLogResultCollector.class.getName() + ".businessNo";
    public static final String RECORD_ID_ATTRIBUTE = OperationLogResultCollector.class.getName() + ".recordId";
    public static final String MODULE_KEY_ATTRIBUTE = OperationLogResultCollector.class.getName() + ".moduleKey";

    /** 与 {@code TraceIdFilter} 写入 MDC 的键一致; 该常量在 filter 内是包级私有, 这里按同一字符串读取。 */
    private static final String TRACE_ID_MDC_KEY = "traceId";

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
            return OperationLogConstants.RESULT_FAILURE;
        }
        return OperationLogConstants.RESULT_SUCCESS;
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

    /**
     * 请求耗时(毫秒): 由拦截器 preHandle 写入的 nanoTime 计算, 缺失或时钟异常时返回 null。
     * 截断到 int 上限, 避免超长挂起请求把耗时写成负数。
     */
    public Integer resolveDurationMs(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(OperationLogInterceptor.START_NANOS_ATTRIBUTE);
        if (!(value instanceof Long startNanos)) {
            return null;
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        if (elapsedNanos < 0) {
            return 0;
        }
        long elapsedMs = elapsedNanos / 1_000_000L;
        return (int) Math.min(elapsedMs, Integer.MAX_VALUE);
    }

    /** 请求 User-Agent 头, 截断 512 字符。 */
    public String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("User-Agent"), 512);
    }

    /** 失败请求的结构化错误码; 成功或异常已被忽略时返回 null。 */
    public String resolveErrorCode(HttpServletRequest request, Exception ex) {
        return OperationLogErrorAttributes.resolveCode(request, ex);
    }

    /** 失败请求的用户可读错误信息(最长 4000); 成功时返回 null。 */
    public String resolveErrorMessage(HttpServletRequest request, Exception ex) {
        return OperationLogErrorAttributes.resolveMessage(request, ex);
    }

    /**
     * 关联技术日志的 Trace ID: 复用 {@code TraceIdFilter} 写入 MDC 的 traceId;
     * 异步/无请求线程中 MDC 为空时返回 null(保持既有语义, 不额外生成)。
     */
    public String resolveTraceId() {
        return truncate(MDC.get(TRACE_ID_MDC_KEY), 64);
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
        ContentCachingRequestWrapper wrapper =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
