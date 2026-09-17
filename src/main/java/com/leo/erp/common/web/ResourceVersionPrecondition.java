package com.leo.erp.common.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

/**
 * 写接口资源版本前置条件的共享解析器。
 *
 * <p>规范机制是自定义请求头 {@link #HEADER}: 值必须是十进制非负整数字符串,
 * 语义等价于 RFC 9110 的强验证器比较(strong comparison)。</p>
 *
 * <p>{@link #LEGACY_HEADER If-Match} 仅作为兼容别名保留: 该头按 RFC 9110 §13.1.1
 * 要求使用强验证器(strong validator), 而数据库单调递增版本号属于弱验证器,
 * 因此这里的用法属于非标准兼容, 不应在新客户端中继续采用。</p>
 *
 * <p>缺少版本前置条件时, 是否按 428 Precondition Required 拒绝由调用方通过
 * {@code required} 参数决定(通常来自模块的 {@code require-resource-version} 配置)。</p>
 */
public final class ResourceVersionPrecondition {

    /** 资源版本前置条件的规范请求/响应头名。 */
    public static final String HEADER = "X-Resource-Version";

    /** 兼容别名(非标准弱验证器用法)。 */
    public static final String LEGACY_HEADER = "If-Match";

    private ResourceVersionPrecondition() {
    }

    /**
     * 解析版本前置条件。
     *
     * @param resourceVersion {@code X-Resource-Version} 头(规范机制, 可为空)
     * @param ifMatch         {@code If-Match} 头(兼容别名, 可为空)
     * @param required        缺少版本时是否拒绝(428)
     * @return 解析后的版本号; 未提供且不强制时返回 {@code null}
     */
    public static Long parse(String resourceVersion, String ifMatch, boolean required) {
        Long canonical = parseCanonical(resourceVersion);
        Long legacy = parseLegacy(ifMatch);
        if (canonical != null && legacy != null && !canonical.equals(legacy)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "资源版本号冲突: " + HEADER + " 与 " + LEGACY_HEADER + " 不一致");
        }
        Long version = canonical != null ? canonical : legacy;
        if (version == null && required) {
            throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED,
                    "缺少资源版本前置条件: 请提供 " + HEADER + " 请求头");
        }
        return version;
    }

    private static Long parseCanonical(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (!text.matches("\\d+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    HEADER + " 版本号不合法: " + value);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    HEADER + " 版本号不合法: " + value);
        }
    }

    private static Long parseLegacy(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (!value.matches("\\d+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    LEGACY_HEADER + " 版本号不合法: " + ifMatch);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    LEGACY_HEADER + " 版本号不合法: " + ifMatch);
        }
    }
}
