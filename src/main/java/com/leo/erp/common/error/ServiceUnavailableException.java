package com.leo.erp.common.error;

/**
 * 依赖服务（Redis、对象存储等）不可用导致当前请求无法完成。
 *
 * <p>统一语义：HTTP 503 + {@link ErrorCode#SERVICE_UNAVAILABLE}。
 * 与 {@link BusinessException} 的区别见 {@code GlobalExceptionHandler} 的处理分支——
 * 这个异常刻意不携带业务语义，只表示「现在不是请求本身的问题，稍后重试可能成功」。</p>
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
