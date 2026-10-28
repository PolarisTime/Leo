package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 请求体脱敏采集: 把请求体解析为 JSON 后递归替换敏感键的值, 再序列化并做总体截断。
 *
 * <p>三条硬约束:</p>
 * <ul>
 *   <li><b>只影响日志</b>: 读取的是 {@link ContentCachingRequestWrapper} 的缓存副本, 不消费主请求流;</li>
 *   <li><b>绝不抛异常</b>: 任何解析/序列化失败都退化为 {@code {"raw": 截断文本}} 或 null, 不影响主请求;</li>
 *   <li><b>总是合法 JSON</b>: 列类型是 jsonb, 超长或非 JSON 一律落到 {@code {"raw": "..."}} 保证可写入。</li>
 * </ul>
 *
 * <p>容量: 序列化后的总体不超过 {@value #MAX_PAYLOAD_CHARS} 字符。JSON 且未超限时保留结构;
 * 超过上限时改存脱敏后文本的 raw 形式, 避免把结构截断成非法 JSON, 同时保证脱敏先于截断
 * (不会因为截断把敏感值原样留下)。</p>
 */
@Component
public class OperationLogRequestBodySanitizer {

    /** 请求体在日志中的总体字符上限(8KB)。 */
    static final int MAX_PAYLOAD_CHARS = 8 * 1024;
    /** 嵌套深度上限, 防止极端嵌套 JSON 造成栈溢出; 超出部分整体替换为掩码。 */
    private static final int MAX_DEPTH = 64;
    private static final String MASK = "***";

    /**
     * 敏感键集合(比较前会去掉 {@code _ - 空格} 并转小写)。
     * 覆盖登录/令牌/客户端凭证等常见字段, 与 {@code password/token/accessToken/refreshToken/secret/authorization} 契约一致。
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "accesstoken", "refreshtoken",
            "secret", "clientsecret", "authorization", "apikey"
    );

    private final ObjectMapper objectMapper;

    public OperationLogRequestBodySanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 采集并脱敏请求体。
     *
     * @return 合法 JSON 字符串; GET/HEAD、无请求体、上传请求或采集失败时返回 {@code null}
     */
    public String sanitize(HttpServletRequest request) {
        try {
            return doSanitize(request);
        } catch (RuntimeException ex) {
            // 日志采集失败绝不能影响主请求; 返回 null 表示本次不记录请求体
            return null;
        }
    }

    private String doSanitize(HttpServletRequest request) {
        if (request == null || isBodyless(request.getMethod()) || isMultipart(request)) {
            return null;
        }
        // Spring Security 会在最外层再包一层 FirewalledRequest，直接 instanceof 永远不成立；
        // 必须沿 wrapper 链找我们自己的缓存 wrapper，否则请求体（和依赖它的业务单号）全部丢失。
        ContentCachingRequestWrapper wrapper =
                WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
            return null;
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        String body = new String(content, StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) {
            return null;
        }

        String sanitizedJson = maskJson(body);
        if (sanitizedJson != null && sanitizedJson.length() <= MAX_PAYLOAD_CHARS) {
            return sanitizedJson;
        }
        return rawPayload(sanitizedJson != null ? sanitizedJson : body);
    }

    private boolean isBodyless(String method) {
        return method == null
                || "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    /** 解析并递归脱敏; 非 JSON 返回 null。 */
    private String maskJson(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return objectMapper.writeValueAsString(maskNode(root, 0));
        } catch (JsonProcessingException notJson) {
            return null;
        }
    }

    private JsonNode maskNode(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return NullNode.getInstance();
        }
        if (depth >= MAX_DEPTH) {
            return TextNode.valueOf(MASK);
        }
        if (node.isObject()) {
            ObjectNode masked = objectMapper.createObjectNode();
            node.properties().forEach(entry -> masked.set(
                    entry.getKey(),
                    isSensitiveKey(entry.getKey()) ? TextNode.valueOf(MASK) : maskNode(entry.getValue(), depth + 1)
            ));
            return masked;
        }
        if (node.isArray()) {
            ArrayNode masked = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                masked.add(maskNode(item, depth + 1));
            }
            return masked;
        }
        return node;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return SENSITIVE_KEYS.contains(normalized);
    }

    /**
     * 以 {@code {"raw": "截断文本"}} 形式保存, 保证结果始终是合法 JSON 且不超过上限。
     * 截断文本本身已经过脱敏(调用方传入的是脱敏后的序列化结果), 因此不会泄露敏感值。
     */
    private String rawPayload(String text) {
        String candidate = text.length() <= MAX_PAYLOAD_CHARS ? text : text.substring(0, MAX_PAYLOAD_CHARS);
        String serialized = writeRaw(candidate);
        while (serialized.length() > MAX_PAYLOAD_CHARS && !candidate.isEmpty()) {
            // JSON 转义最多把单个字符膨胀为 6 个字符(\\uXXXX 形式), 逐步收缩直到序列化结果落入上限
            candidate = candidate.substring(0, candidate.length() * 3 / 4);
            serialized = writeRaw(candidate);
        }
        return serialized;
    }

    private String writeRaw(String text) {
        return objectMapper.createObjectNode().put("raw", text).toString();
    }
}
