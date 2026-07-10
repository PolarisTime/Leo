package com.leo.erp.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void shouldHaveSuccess() {
        assertThat(ErrorCode.SUCCESS.getCode()).isEqualTo(0);
        assertThat(ErrorCode.SUCCESS.getMessage()).isEqualTo("OK");
    }

    @Test
    void shouldHaveValidationError() {
        assertThat(ErrorCode.VALIDATION_ERROR.getCode()).isEqualTo(4000);
        assertThat(ErrorCode.VALIDATION_ERROR.getMessage()).isEqualTo("请求参数不合法");
    }

    @Test
    void shouldHaveUnauthorized() {
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(4010);
        assertThat(ErrorCode.UNAUTHORIZED.getMessage()).isEqualTo("未登录或登录已失效");
    }

    @Test
    void shouldHaveForbidden() {
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(4030);
        assertThat(ErrorCode.FORBIDDEN.getMessage()).isEqualTo("无权访问");
    }

    @Test
    void shouldHaveNotFound() {
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(4040);
        assertThat(ErrorCode.NOT_FOUND.getMessage()).isEqualTo("资源不存在");
    }

    @Test
    void shouldHaveBusinessError() {
        assertThat(ErrorCode.BUSINESS_ERROR.getCode()).isEqualTo(4220);
        assertThat(ErrorCode.BUSINESS_ERROR.getMessage()).isEqualTo("业务处理失败");
    }

    @Test
    void shouldHaveRateLimited() {
        assertThat(ErrorCode.RATE_LIMITED.getCode()).isEqualTo(4290);
        assertThat(ErrorCode.RATE_LIMITED.getMessage()).isEqualTo("请求过于频繁，请稍后再试");
    }

    @Test
    void shouldHaveInternalError() {
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(5000);
        assertThat(ErrorCode.INTERNAL_ERROR.getMessage()).isEqualTo("系统内部错误");
    }

    @Test
    void shouldHaveSessionEvicted() {
        assertThat(ErrorCode.SESSION_EVICTED.getCode()).isEqualTo(4011);
    }

    @Test
    void shouldHaveRefreshTokenReuseConflict() {
        assertThat(ErrorCode.REFRESH_TOKEN_REUSE_CONFLICT.getCode()).isEqualTo(4091);
    }

    @Test
    void shouldHaveConcurrentModificationConflict() {
        ErrorCode errorCode = ErrorCode.valueOf("CONCURRENT_MODIFICATION");

        assertThat(errorCode.getCode()).isEqualTo(4090);
        assertThat(errorCode.getMessage()).isEqualTo("数据已被其他请求修改，请刷新后重试");
    }

    @Test
    void shouldHaveAllErrorCodes() {
        assertThat(ErrorCode.values()).hasSize(11);
    }
}
