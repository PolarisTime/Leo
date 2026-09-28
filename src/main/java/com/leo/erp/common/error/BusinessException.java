package com.leo.erp.common.error;

import com.leo.erp.common.api.ApiFieldError;

import java.util.List;

/**
 * 业务异常: 由全局异常处理器按 {@link ErrorCode} 映射为 RFC 9457 ProblemDetail。
 *
 * <p>可选携带 {@link ApiFieldError} 明细(如价格条目键重复), 由全局处理器写入
 * {@code errors} 属性; 绝大多数场景用两参构造即可, 错误响应形状保持不变。</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiFieldError> errors;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<ApiFieldError> errors) {
        super(message);
        this.errorCode = errorCode;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 字段级错误明细, 无明细时为空列表(不为 null)。 */
    public List<ApiFieldError> getErrors() {
        return errors;
    }
}
