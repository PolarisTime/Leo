package com.leo.erp.common.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 单据/业务状态代码层枚举。
 *
 * <p>数据库与 API 仍然存储、传输 {@link #label()} 中文值，本枚举只用于代码层消除魔法字符串，
 * 不改变任何持久化值与对外契约。</p>
 */
public enum DocumentStatus {

    // 通用状态
    NORMAL("NORMAL", "正常"),
    DISABLED("DISABLED", "禁用"),

    // 单据状态
    DRAFT("DRAFT", "草稿"),
    /** 仅用于识别历史数据，新流程不得写入。 */
    @Deprecated
    PRE_OUTBOUND("PRE_OUTBOUND", "预出库"),
    AUDITED("AUDITED", "已审核"),
    /** 销售合同已发出: 已审核之后发出, 只能继续归档, 不允许直接作废。 */
    ISSUED("ISSUED", "已发出"),
    COMPLETED("COMPLETED", "已完成"),
    /** 销售合同作废: 终态之一, 未被删除但不再计入有效合同额度。 */
    VOIDED("VOIDED", "作废"),

    // 财务状态（历史迁移前数据）
    /** 仅用于兼容迁移前的付款数据，新流程统一使用 {@link #AUDITED}。 */
    @Deprecated
    LEGACY_PAID("LEGACY_PAID", "已付款"),
    /** 仅用于兼容迁移前的收款数据，新流程统一使用 {@link #AUDITED}。 */
    @Deprecated
    LEGACY_RECEIVED("LEGACY_RECEIVED", "已收款"),

    // 业务完成状态
    PURCHASE_COMPLETED("PURCHASE_COMPLETED", "完成采购"),
    SALES_COMPLETED("SALES_COMPLETED", "完成销售"),
    INBOUND_COMPLETED("INBOUND_COMPLETED", "完成入库"),
    DELIVERY_VERIFICATION("DELIVERY_VERIFICATION", "交付核定"),

    // 签署状态
    SIGNED("SIGNED", "已签署"),
    UNSIGNED("UNSIGNED", "未签署"),
    UNAUDITED("UNAUDITED", "未审核"),
    EXECUTING("EXECUTING", "执行中"),
    ARCHIVED("ARCHIVED", "归档"),

    // 待处理状态
    PENDING_CONFIRM("PENDING_CONFIRM", "待确认"),
    CONFIRMED("CONFIRMED", "已确认"),
    PENDING_AUDIT("PENDING_AUDIT", "待审核");

    private static final Map<String, DocumentStatus> BY_LABEL;
    private static final Map<String, DocumentStatus> BY_CODE;
    private static final Set<String> LABELS;

    static {
        Map<String, DocumentStatus> byLabel = new LinkedHashMap<>();
        Map<String, DocumentStatus> byCode = new LinkedHashMap<>();
        Set<String> labels = new LinkedHashSet<>();
        for (DocumentStatus status : values()) {
            byLabel.put(status.label, status);
            byCode.put(status.code, status);
            labels.add(status.label);
        }
        BY_LABEL = Collections.unmodifiableMap(byLabel);
        BY_CODE = Collections.unmodifiableMap(byCode);
        LABELS = Collections.unmodifiableSet(labels);
    }

    private final String code;
    private final String label;

    DocumentStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 英文稳定编码，例如 {@code DRAFT}。 */
    public String code() {
        return code;
    }

    /** 中文状态值，与 {@link StatusConstants} 中对应常量逐字一致。 */
    public String label() {
        return label;
    }

    /**
     * 按中文状态值查找枚举。
     *
     * @param label 中文状态值，允许首尾空白
     * @return 匹配的枚举；{@code null}/空白/未知值返回 {@link Optional#empty()}
     */
    public static Optional<DocumentStatus> fromLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String normalized = label.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_LABEL.get(normalized));
    }

    /**
     * 按英文稳定编码查找枚举。
     *
     * @param code 英文编码，例如 {@code DRAFT}
     * @return 匹配的枚举；{@code null}/空白/未知值返回 {@link Optional#empty()}
     */
    public static Optional<DocumentStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(normalized));
    }

    /**
     * 按英文稳定编码获取中文状态值。
     *
     * @param code 英文编码，例如 {@code DRAFT}
     * @return 对应的中文状态值；{@code null}/空白/未知编码返回 {@code null}
     */
    public static String labelOf(String code) {
        return fromCode(code).map(DocumentStatus::label).orElse(null);
    }

    /** 全部中文状态值（去重，按枚举声明顺序）。 */
    public static Set<String> labels() {
        return LABELS;
    }
}
