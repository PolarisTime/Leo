package com.leo.erp.common.exception;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 429（{@link ErrorCode#TOO_MANY_REQUESTS}）契约测试。
 *
 * <p>背景：2026-09-30 生产量级复测发现，导出并发闸门（{@code ExportConcurrencyGuard}）抛出的
 * 429 虽然 body 完全符合 RFC 9457，却<strong>缺少 {@code Retry-After} 响应头</strong>——
 * 而 {@code module-exports} 的 OpenAPI 注解与 Guard 的 Javadoc 都明确承诺携带该头。
 * 契约与实现不一致，客户端拿不到退避提示只能盲重试。本测试把「429 必须带 Retry-After」
 * 与「其它业务码不得被顺手加上该头」双向锁定。</p>
 *
 * <p>注：{@code RateLimitFilter} 的 429 在过滤器内按窗口精确推导 {@code Retry-After}
 * 并直写响应，不经过本处理器；两条 429 路径互不干扰。</p>
 */
class GlobalExceptionHandlerTooManyRequestsTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai"));

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TooManyRequestsProbeController())
                .setControllerAdvice(handler)
                .build();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v2.0/module-exports");
        return request;
    }

    private ProblemDetail problemOf(ResponseEntity<?> response) {
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        return (ProblemDetail) response.getBody();
    }

    /** 429 必须携带 Retry-After，否则客户端只能盲重试（OpenAPI 与 Guard Javadoc 均已承诺）。 */
    @Test
    void tooManyRequests_carriesRetryAfterAndProblemDetail() {
        ResponseEntity<?> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "导出任务过多，请稍后重试"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");

        ProblemDetail problem = problemOf(response);
        assertThat(problem.getProperties().get("code")).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
        assertThat(problem.getType().toString()).isEqualTo("urn:leo:problem:too-many-requests");
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
    }

    /** 反向锁定：其它业务码不得被顺手加上 Retry-After（该头只属于 429 与 503 依赖分支）。 */
    @Test
    void otherBusinessCodes_doNotGainRetryAfter() {
        ResponseEntity<?> notFound = handler.handleBusinessException(
                new BusinessException(ErrorCode.NOT_FOUND, "单据不存在"), request());
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();

        ResponseEntity<?> unprocessable = handler.handleBusinessException(
                new BusinessException(ErrorCode.VALIDATION_ERROR, "参数不合法"), request());
        assertThat(unprocessable.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(unprocessable.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
    }

    /** 经由 BusinessServiceUnavailable 抛出的 503 保持既有行为（本轮不改它的响应头，范围只锁定 429）。 */
    @Test
    void serviceUnavailableViaBusinessException_keepsExistingContract() {
        ResponseEntity<?> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "依赖暂不可用"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(problemOf(response).getProperties().get("code"))
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
    }

    /** MockMvc 端到端：验证真实 advice 选择路径下 429 + Retry-After + problem+json 一并生效。 */
    @Test
    void mockMvc_endToEnd_429CarriesRetryAfterHeader() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_REQUESTS.getCode()))
                .andExpect(jsonPath("$.type").value("urn:leo:problem:too-many-requests"));
    }

    @RestController
    static class TooManyRequestsProbeController {
        @PostMapping("/probe")
        public Map<String, String> probe(@RequestBody Map<String, String> ignored) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "导出任务过多，请稍后重试");
        }
    }
}
