package com.leo.erp.system.operationlog.support;

import com.leo.erp.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 失败请求的结构化错误采集: 把异常翻译为 {@code error_code}/{@code error_message}。
 *
 * <p>为什么需要 request attribute 中转: 业务异常通常被 {@code GlobalExceptionHandler}
 * 转成 ProblemDetail, 此时 {@code HandlerInterceptor#afterCompletion} 收到的 {@code ex} 是 null,
 * 只在拦截器里读异常会导致绝大多数 4xx 业务失败没有错误码。因此由
 * {@link OperationLogErrorCaptureResolver} 在异常解析前先写 attribute, 拦截器侧优先读 attribute,
 * 仅在异常未被解析时回退到 {@code ex}。</p>
 */
public final class OperationLogErrorAttributes {

    public static final String CODE_ATTRIBUTE = OperationLogErrorAttributes.class.getName() + ".code";
    public static final String MESSAGE_ATTRIBUTE = OperationLogErrorAttributes.class.getName() + ".message";

    static final int MAX_CODE_LENGTH = 64;
    static final int MAX_MESSAGE_LENGTH = 4000;

    private OperationLogErrorAttributes() {
    }

    /**
     * 记录异常对应的错误码与错误信息; 多次解析同一请求时以最后一次为准。
     *
     * <p>返回写入的错误码而不是 void: SpotBugs 的 {@code UC_USELESS_VOID_METHOD} 只看方法体内
     * 调用的"无副作用方法", 而 {@code HttpServletRequest#setAttribute} 是接口方法、它推不出副作用,
     * 于是把整个方法判成无用(实测 CI 因此红)。返回错误码既消掉这个误判, 也让调用方在需要时能直接拿到码。</p>
     */
    public static String capture(HttpServletRequest request, Exception ex) {
        if (request == null || ex == null) {
            return null;
        }
        String code = truncate(codeOf(ex), MAX_CODE_LENGTH);
        request.setAttribute(CODE_ATTRIBUTE, code);
        request.setAttribute(MESSAGE_ATTRIBUTE, truncate(messageOf(ex), MAX_MESSAGE_LENGTH));
        return code;
    }

    /** 优先取异常解析阶段记录的 attribute, 没有时回退到未解析的异常本身。 */
    public static String resolveCode(HttpServletRequest request, Exception ex) {
        String captured = readAttribute(request, CODE_ATTRIBUTE);
        if (captured != null) {
            return captured;
        }
        return ex == null ? null : truncate(codeOf(ex), MAX_CODE_LENGTH);
    }

    /** 优先取异常解析阶段记录的 attribute, 没有时回退到未解析的异常本身。 */
    public static String resolveMessage(HttpServletRequest request, Exception ex) {
        String captured = readAttribute(request, MESSAGE_ATTRIBUTE);
        if (captured != null) {
            return captured;
        }
        return ex == null ? null : truncate(messageOf(ex), MAX_MESSAGE_LENGTH);
    }

    /**
     * 错误码: {@link BusinessException} 取业务码(如 4000), 其它异常取异常类简名。
     * 这样既能按业务错误码统计, 又能区分未预期异常的类型。
     */
    private static String codeOf(Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return String.valueOf(businessException.getErrorCode().getCode());
        }
        return ex.getClass().getSimpleName();
    }

    /** 用户可读信息: 业务异常优先异常消息, 为空时回退错误码默认文案; 其它异常取异常消息。 */
    private static String messageOf(Exception ex) {
        String message = trimToNull(ex.getMessage());
        if (ex instanceof BusinessException businessException) {
            return message != null ? message : businessException.getErrorCode().getMessage();
        }
        return message != null ? message : ex.getClass().getSimpleName();
    }

    private static String readAttribute(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(name);
        return value == null ? null : trimToNull(String.valueOf(value));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
