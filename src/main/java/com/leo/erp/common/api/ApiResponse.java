package com.leo.erp.common.api;

import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.DateTimeFormatSupport;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, DateTimeFormatSupport.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data, DateTimeFormatSupport.now());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, DateTimeFormatSupport.now());
    }
}
