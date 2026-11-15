package com.leo.erp.common.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ResponseSupport} 契约测试：201 必须带 Location；响应体没有 id 或 id 读取失败时
 * 只能降级为「无 Location 的 201」，不能抛异常变成 500（否则资源已创建、客户端重试会重复创建）。
 */
class ResponseSupportTest {

    record CreatedBody(String id, String name) {
    }

    record NoIdBody(String importBatchNo) {
    }

    static final class ThrowingIdBody {
        public String id() {
            throw new IllegalStateException("boom");
        }
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2.0/customers");
        request.setServerName("localhost");
        request.setScheme("http");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void created_shouldReturn201WithLocationWhenBodyHasId() {
        bindRequest();

        ResponseEntity<CreatedBody> response =
                ResponseSupport.created("/customers", new CreatedBody("42", "客户"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("/v2.0/customers/42");
        assertThat(response.getBody()).isEqualTo(new CreatedBody("42", "客户"));
    }

    @Test
    void created_shouldDegradeTo201WithoutLocationWhenBodyHasNoId() {
        bindRequest();

        ResponseEntity<NoIdBody> response =
                ResponseSupport.created("/material-imports", new NoIdBody("IMP-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    void created_shouldNotThrowWhenIdAccessorFails() {
        bindRequest();

        ResponseEntity<ThrowingIdBody> response =
                ResponseSupport.created("/broken", new ThrowingIdBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    void noContent_shouldReturn204WithoutBody() {
        ResponseEntity<Void> response = ResponseSupport.noContent();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}
