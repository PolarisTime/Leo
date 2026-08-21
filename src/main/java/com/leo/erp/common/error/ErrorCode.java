package com.leo.erp.common.error;

public enum ErrorCode {
    SUCCESS(0, "OK"),
    VALIDATION_ERROR(4000, "请求参数不合法"),
    UNAUTHORIZED(4010, "未登录或登录已失效"),
    FORBIDDEN(4030, "无权访问"),
    NOT_FOUND(4040, "资源不存在"),
    METHOD_NOT_ALLOWED(4050, "请求方法不支持"),
    NOT_ACCEPTABLE(4060, "响应类型不支持"),
    PAYLOAD_TOO_LARGE(4130, "请求内容过大"),
    UNSUPPORTED_MEDIA_TYPE(4150, "请求内容类型不支持"),
    BUSINESS_ERROR(4220, "业务处理失败"),
    SESSION_EVICTED(4011, "您的账号已在其他设备登录，当前会话已被登出"),
    CONCURRENT_MODIFICATION(4090, "数据已被其他请求修改，请刷新后重试"),
    REFRESH_TOKEN_REUSE_CONFLICT(4091, "登录状态正在刷新，请稍后重试"),
    TOO_MANY_REQUESTS(4290, "请求过于频繁，请稍后重试"),
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
