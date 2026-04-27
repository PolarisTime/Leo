package com.leo.erp.common.error;

public enum ErrorCode {
    SUCCESS(0, "OK"),
    VALIDATION_ERROR(4000, "请求参数不合法"),
    UNAUTHORIZED(4010, "未登录或登录已失效"),
    FORBIDDEN(4030, "无权访问"),
    NOT_FOUND(4040, "资源不存在"),
    BUSINESS_ERROR(4220, "业务处理失败"),
    INTERNAL_ERROR(5000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
