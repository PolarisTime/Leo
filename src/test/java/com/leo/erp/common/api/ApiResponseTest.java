package com.leo.erp.common.api;

import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void successIncludesCurrentRateLimitSnapshot() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RateLimitContext.set(request, RateLimitContext.Snapshot.allowed(150, 149));

        ApiResponse<String> response = ApiResponse.success("成功", "ok");

        assertThat(response.rateLimit()).isNotNull();
        assertThat(response.rateLimit().limit()).isEqualTo(150);
        assertThat(response.rateLimit().remaining()).isEqualTo(149);
        assertThat(response.rateLimit().retryAfterSeconds()).isNull();
    }

    @Test
    void failureCanIncludeExplicitRateLimitSnapshot() {
        ApiResponse<Void> response = ApiResponse.failure(
                com.leo.erp.common.error.ErrorCode.RATE_LIMITED,
                "limited",
                RateLimitContext.Snapshot.rejected(10, 3)
        );

        assertThat(response.rateLimit()).isNotNull();
        assertThat(response.rateLimit().limit()).isEqualTo(10);
        assertThat(response.rateLimit().remaining()).isZero();
        assertThat(response.rateLimit().resetSeconds()).isEqualTo(3);
        assertThat(response.rateLimit().retryAfterSeconds()).isEqualTo(3);
    }

    @Test
    void successWithDataReturnsSuccessCode() {
        ApiResponse<Integer> response = ApiResponse.success(42);

        assertThat(response.code()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.timestamp()).isNotBlank();
    }

    @Test
    void successWithMessageReturnsVoid() {
        ApiResponse<Void> response = ApiResponse.success("操作成功");

        assertThat(response.code()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.message()).isEqualTo("操作成功");
        assertThat(response.data()).isNull();
    }

    @Test
    void successWithMessageAndDataAndRateLimit() {
        RateLimitContext.Snapshot snapshot = RateLimitContext.Snapshot.allowed(100, 99);
        ApiResponse<String> response = ApiResponse.success("msg", "val", snapshot);

        assertThat(response.code()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(response.message()).isEqualTo("msg");
        assertThat(response.data()).isEqualTo("val");
        assertThat(response.rateLimit()).isEqualTo(snapshot);
    }

    @Test
    void failureWithErrorCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.NOT_FOUND, "找不到");

        assertThat(response.code()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(response.message()).isEqualTo("找不到");
        assertThat(response.data()).isNull();
    }

    @Test
    void failureIncludesTraceIdWhenPresentInMdc() {
        MDC.put("traceId", "abc-123");

        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.INTERNAL_ERROR, "错误");

        assertThat(response.traceId()).isEqualTo("abc-123");
    }

    @Test
    void failureHasNullTraceIdWhenMdcEmpty() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.INTERNAL_ERROR, "错误");

        assertThat(response.traceId()).isNull();
    }

    @Test
    void constructorWith5ArgsAcceptsTraceId() {
        ApiResponse<String> response = new ApiResponse<>(200, "ok", "data", "2024-01-01", "trace-1");

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.traceId()).isEqualTo("trace-1");
        assertThat(response.rateLimit()).isNull();
    }

    @Test
    void constructorWith4Args() {
        ApiResponse<String> response = new ApiResponse<>(200, "ok", "data", "2024-01-01");

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.traceId()).isNull();
        assertThat(response.rateLimit()).isNull();
    }
}
