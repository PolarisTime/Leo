package com.leo.erp.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.DateTimeFormatSupport;
import org.slf4j.MDC;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String timestamp,
        String traceId,
        RateLimitContext.Snapshot rateLimit
) {

    private static final String MDC_TRACE_KEY = "traceId";

    public ApiResponse(int code, String message, T data, String timestamp) {
        this(code, message, data, timestamp, null, null);
    }

    public ApiResponse(int code, String message, T data, String timestamp, String traceId) {
        this(code, message, data, timestamp, traceId, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, DateTimeFormatSupport.now(), null, currentRateLimit());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data, DateTimeFormatSupport.now(), null, currentRateLimit());
    }

    public static <T> ApiResponse<T> success(String message, T data, RateLimitContext.Snapshot rateLimit) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data, DateTimeFormatSupport.now(), null, rateLimit);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, null, DateTimeFormatSupport.now(), null, currentRateLimit());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, DateTimeFormatSupport.now(), currentTraceId(), currentRateLimit());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message, RateLimitContext.Snapshot rateLimit) {
        return new ApiResponse<>(errorCode.getCode(), message, null, DateTimeFormatSupport.now(), currentTraceId(), rateLimit);
    }

    private static String currentTraceId() {
        try {
            String traceId = MDC.get(MDC_TRACE_KEY);
            return (traceId != null && !traceId.isBlank()) ? traceId : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static RateLimitContext.Snapshot currentRateLimit() {
        return RateLimitContext.current();
    }
}
