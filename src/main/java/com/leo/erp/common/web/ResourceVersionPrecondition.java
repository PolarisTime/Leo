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

    /**
     * 校验客户端声明的期望版本与实体当前 {@code @Version} 是否一致。
     *
     * <p>JPA {@code @Version} 只能检测「同一事务读-改-写窗口」内的并发：A 打开表单、B 先保存、
     * A 再保存时，两次事务都会读到最新版本并各自成功，后写者静默覆盖先写者的修改。
     * 客户端带来的期望版本号正是把「我编辑的是哪一版」显式化的通道，因此写入口必须用它做前置校验。</p>
     *
     * <p>语义：</p>
     * <ul>
     *   <li>{@code expectedVersion == null}（模块未强制 / 灰度兼容期）→ 不做校验，行为与改造前一致；</li>
     *   <li>一致 → 放行，由 JPA 继续负责事务内的并发保护；</li>
     *   <li>不一致或实体无版本 → {@link ErrorCode#PRECONDITION_FAILED}（412）。</li>
     * </ul>
     *
     * <p>缺失版本头返回 428 由 {@link #parse(String, String, boolean)} 负责；本方法只处理「给了但对不上」。</p>
     *
     * @param expectedVersion 客户端期望版本（可为 {@code null}）
     * @param currentVersion  实体当前版本
     * @param resourceLabel   用于错误提示的资源名（如「采购订单」）
     */
    public static void verify(Long expectedVersion, Long currentVersion, String resourceLabel) {
        if (expectedVersion == null) {
            return;
        }
        if (currentVersion == null || !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(
                    ErrorCode.PRECONDITION_FAILED,
                    resourceLabel + "版本已变更，请刷新后重试（期望 v" + expectedVersion
                            + "，当前 v" + currentVersion + "）"
            );
        }
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
