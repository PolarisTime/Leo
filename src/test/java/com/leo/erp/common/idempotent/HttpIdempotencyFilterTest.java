package com.leo.erp.common.idempotent;

import com.leo.erp.common.api.ApiErrorResponseWriter;
import com.leo.erp.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.mock.web.MockPart;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpIdempotencyFilterTest {

    private static final String IDEMPOTENCY_KEY = "idem-key";
    private static final String JSON_BODY = "{\"successCount\":2}";

    @Mock
    private HttpIdempotencyService idempotencyService;

    @Mock
    private ApiErrorResponseWriter errorResponseWriter;

    private HttpIdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HttpIdempotencyFilter(idempotencyService, errorResponseWriter);
    }

    @Test
    void multipartDuplicate_shouldReplayCachedResponse() throws Exception {
        HttpIdempotencyService.CachedResponse cached = new HttpIdempotencyService.CachedResponse(
                201,
                MediaType.APPLICATION_JSON_VALUE,
                Base64.getEncoder().encodeToString(JSON_BODY.getBytes(StandardCharsets.UTF_8)),
                Map.of("Location", "/api/v2.0/material-imports/1"));
        when(idempotencyService.start(anyString(), anyString(), any(Duration.class)))
                .thenReturn(HttpIdempotencyService.Decision.duplicateCompleted(cached));

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(multipartRequest(IDEMPOTENCY_KEY, "payload"), response,
                (request, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo(JSON_BODY);
        assertThat(response.getHeader("Location")).isEqualTo("/api/v2.0/material-imports/1");
        verify(idempotencyService).start(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void multipartFirstSubmission_shouldRecordCompletedResponse() throws Exception {
        when(idempotencyService.start(anyString(), anyString(), any(Duration.class)))
                .thenReturn(HttpIdempotencyService.Decision.acquired());
        when(idempotencyService.markCompleted(anyString(), anyString(), any(Duration.class), any()))
                .thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(multipartRequest(IDEMPOTENCY_KEY, "payload"), response, (request, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(201);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.getWriter().write(JSON_BODY);
        });

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo(JSON_BODY);

        ArgumentCaptor<HttpIdempotencyService.CachedResponse> captor =
                ArgumentCaptor.forClass(HttpIdempotencyService.CachedResponse.class);
        verify(idempotencyService).markCompleted(
                anyString(), anyString(), any(Duration.class), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(201);
        assertThat(captor.getValue().body()).isEqualTo(JSON_BODY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void multipartFingerprint_shouldVaryWithFileContent() throws Exception {
        when(idempotencyService.start(anyString(), anyString(), any(Duration.class)))
                .thenReturn(HttpIdempotencyService.Decision.acquired());
        when(idempotencyService.markCompleted(anyString(), anyString(), any(Duration.class), any()))
                .thenReturn(true);

        filter.doFilter(multipartRequest(IDEMPOTENCY_KEY, "content-a"),
                new MockHttpServletResponse(), (request, response) -> { });
        filter.doFilter(multipartRequest(IDEMPOTENCY_KEY, "content-b"),
                new MockHttpServletResponse(), (request, response) -> { });

        ArgumentCaptor<String> fingerprints = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService, times(2)).start(anyString(), fingerprints.capture(), any(Duration.class));
        List<String> captured = fingerprints.getAllValues();
        assertThat(captured.get(0)).isNotEqualTo(captured.get(1));
    }

    @Test
    void multipartExceedingThreshold_shouldSkipIdempotencyAndContinue() throws Exception {
        MockMultipartHttpServletRequest request = multipartRequest(IDEMPOTENCY_KEY, "payload");
        request.setContent(new byte[(int) HttpIdempotencyFilter.MULTIPART_MAX_IDEMPOTENT_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        verify(idempotencyService, never()).start(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void multipartWithoutIdempotencyKey_shouldSkipIdempotency() throws Exception {
        MockMultipartHttpServletRequest request = multipartRequest(null, "payload");
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        verify(idempotencyService, never()).start(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void jsonDuplicate_shouldReplayCachedResponse() throws Exception {
        HttpIdempotencyService.CachedResponse cached = new HttpIdempotencyService.CachedResponse(
                201,
                MediaType.APPLICATION_JSON_VALUE,
                Base64.getEncoder().encodeToString(JSON_BODY.getBytes(StandardCharsets.UTF_8)),
                Map.of());
        when(idempotencyService.start(anyString(), anyString(), any(Duration.class)))
                .thenReturn(HttpIdempotencyService.Decision.duplicateCompleted(cached));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2.0/sales-orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"customerId\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader(HttpIdempotencyFilter.HEADER, IDEMPOTENCY_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo(JSON_BODY);
    }

    private MockMultipartHttpServletRequest multipartRequest(String idempotencyKey, String fileContent) {
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v2.0/material-imports");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.setContent("multipart-body".getBytes(StandardCharsets.UTF_8));
        request.addPart(new MockPart("moduleKey", "material", "material".getBytes(StandardCharsets.UTF_8)));
        request.addPart(new MockPart("file", "material.xlsx", fileContent.getBytes(StandardCharsets.UTF_8)));
        if (idempotencyKey != null) {
            request.addHeader(HttpIdempotencyFilter.HEADER, idempotencyKey);
        }
        return request;
    }

    // ---- 幂等冲突的状态码语义（2026-09-30 报告 P3 项）---------------------------------
    // 冲突（同一幂等键正在处理 / 该键已用于不同请求体）必须返回 409，
    // 而不是 422：422 与「字段校验失败」同码，客户端无法区分，也不符合 REST 惯例。
    // 这三条断言同时也是「响应不再静默」的回归保护——写出前必须留下结构化日志。

    @Test
    void duplicatePending_shouldReturn409Conflict() throws Exception {
        when(idempotencyService.start(anyString(), anyString(), any(Duration.class)))
                .thenReturn(HttpIdempotencyService.Decision.duplicatePending());

        MockHttpServletRequest request = jsonRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });

        ArgumentCaptor<HttpStatus> status = ArgumentCaptor.forClass(HttpStatus.class);
        ArgumentCaptor<ErrorCode> code = ArgumentCaptor.forClass(ErrorCode.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(errorResponseWriter).write(any(), any(), status.capture(), code.capture(), message.capture());
        assertThat(status.getValue()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code.getValue()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(message.getValue()).contains("请勿重复提交");
    }

    @Test
    void reusedKeyWithDifferentPayload_shouldReturn409Conflict() throws Exception {
        when(idempotencyService.start(anyString(), anyString(), any(Duration.class)))
                .thenReturn(HttpIdempotencyService.Decision.parameterMismatch());

        MockHttpServletRequest request = jsonRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });

        ArgumentCaptor<HttpStatus> status = ArgumentCaptor.forClass(HttpStatus.class);
        ArgumentCaptor<ErrorCode> code = ArgumentCaptor.forClass(ErrorCode.class);
        verify(errorResponseWriter).write(any(), any(), status.capture(), code.capture(), any());
        assertThat(status.getValue()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code.getValue()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void idempotencyServiceUnavailable_shouldReturn503Not500() throws Exception {
        when(idempotencyService.start(anyString(), anyString(), any(Duration.class)))
                .thenReturn(HttpIdempotencyService.Decision.unavailable());

        MockHttpServletRequest request = jsonRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });

        ArgumentCaptor<HttpStatus> status = ArgumentCaptor.forClass(HttpStatus.class);
        ArgumentCaptor<ErrorCode> code = ArgumentCaptor.forClass(ErrorCode.class);
        verify(errorResponseWriter).write(any(), any(), status.capture(), code.capture(), any());
        assertThat(status.getValue()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // 必须是 5030（service-unavailable）而非 5000（internal-error），
        // 否则监控无法区分「Redis 挂了」与「代码有问题」。
        assertThat(code.getValue()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    private MockHttpServletRequest jsonRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2.0/customers");
        request.addHeader(HttpIdempotencyFilter.HEADER, IDEMPOTENCY_KEY);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(JSON_BODY.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
