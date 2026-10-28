package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求体采集与脱敏单测。
 *
 * <p>审计日志会长期保存，密码/令牌一旦原样落库就是事故；这里锁定三条契约：
 * 敏感键必须脱敏、非 JSON/超长必须退化为 raw、GET/上传不采集。</p>
 */
class OperationLogRequestBodySanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OperationLogRequestBodySanitizer sanitizer =
            new OperationLogRequestBodySanitizer(objectMapper);

    /** 包一层 ContentCachingRequestWrapper，并先把 body 读出来触发缓存（与真实链路一致）。 */
    private ContentCachingRequestWrapper wrapper(String method, String body, String contentType) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v2.0/materials");
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();
        return wrapper;
    }

    @Test
    void masksSensitiveKeysIncludingNestedAndArrays() throws IOException {
        String body = """
                {"loginName":"admin","password":"secret-value",
                 "nested":{"accessToken":"tok-1","keep":1},
                 "list":[{"refreshToken":"tok-2","price":3280}]}
                """;
        String sanitized = sanitizer.sanitize(wrapper("POST", body, "application/json"));
        assertThat(sanitized).isNotNull();
        JsonNode node = objectMapper.readTree(sanitized);

        assertThat(node.get("password").asText()).isEqualTo("***");
        assertThat(node.at("/nested/accessToken").asText()).isEqualTo("***");
        assertThat(node.at("/nested/keep").asInt()).isEqualTo(1);
        assertThat(node.at("/list/0/refreshToken").asText()).isEqualTo("***");
        assertThat(node.at("/list/0/price").asInt()).isEqualTo(3280);
        assertThat(sanitized).doesNotContain("secret-value").doesNotContain("tok-1").doesNotContain("tok-2");
    }

    @Test
    void keepsStructuredJsonWhenWithinLimit() throws IOException {
        String sanitized = sanitizer.sanitize(wrapper("PUT", "{\"price\":3280}", "application/json"));
        assertThat(objectMapper.readTree(sanitized).get("price").asInt()).isEqualTo(3280);
    }

    @Test
    void wrapsNonJsonBodyAsRaw() throws IOException {
        String sanitized = sanitizer.sanitize(wrapper("POST", "price=3280&password=x", "text/plain"));
        assertThat(objectMapper.readTree(sanitized).get("raw").asText()).contains("price=3280");
    }

    @Test
    void truncatesOversizedBodyAndKeepsValidJson() throws IOException {
        String huge = "{\"remark\":\"" + "a".repeat(20_000) + "\"}";
        String sanitized = sanitizer.sanitize(wrapper("POST", huge, "application/json"));
        assertThat(sanitized.length()).isLessThanOrEqualTo(OperationLogRequestBodySanitizer.MAX_PAYLOAD_CHARS);
        assertThat(objectMapper.readTree(sanitized)).isNotNull();
    }

    @Test
    void findsCacheWrapperEvenWhenSecurityWrapsItAgain() throws IOException {
        // 回归：Spring Security 会在最外层再包一层 wrapper，直接 instanceof 会永远失败，
        // 表现为「请求体与业务单号静默丢失」。必须能从 wrapper 链里找到缓存层。
        ContentCachingRequestWrapper cached = wrapper("POST", "{\"password\":\"p\",\"keep\":1}", "application/json");
        HttpServletRequestWrapper firewalled = new HttpServletRequestWrapper(cached) { };

        String sanitized = sanitizer.sanitize(firewalled);

        assertThat(sanitized).isNotNull();
        JsonNode node = objectMapper.readTree(sanitized);
        assertThat(node.get("password").asText()).isEqualTo("***");
        assertThat(node.get("keep").asInt()).isEqualTo(1);
    }

    @Test
    void ignoresBodylessAndMultipartRequests() throws IOException {
        assertThat(sanitizer.sanitize(wrapper("GET", "{\"a\":1}", "application/json"))).isNull();
        assertThat(sanitizer.sanitize(wrapper("POST", "binary", "multipart/form-data; boundary=x"))).isNull();
    }
}
