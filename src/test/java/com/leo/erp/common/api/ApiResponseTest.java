package com.leo.erp.common.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
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
}
