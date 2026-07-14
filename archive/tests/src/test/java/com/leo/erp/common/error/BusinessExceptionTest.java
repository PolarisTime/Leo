package com.leo.erp.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void shouldCreateWithErrorCodeAndMessage() {
        BusinessException exception = new BusinessException(ErrorCode.VALIDATION_ERROR, "参数错误");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(exception.getMessage()).isEqualTo("参数错误");
    }

    @Test
    void shouldExtendRuntimeException() {
        BusinessException exception = new BusinessException(ErrorCode.INTERNAL_ERROR, "内部错误");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldSupportDifferentErrorCodes() {
        assertThat(new BusinessException(ErrorCode.UNAUTHORIZED, "未登录").getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        assertThat(new BusinessException(ErrorCode.FORBIDDEN, "无权限").getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(new BusinessException(ErrorCode.NOT_FOUND, "不存在").getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        assertThat(new BusinessException(ErrorCode.BUSINESS_ERROR, "业务错误").getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }
}
