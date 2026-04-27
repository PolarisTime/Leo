package com.leo.erp.common.support;

import java.util.Set;

public final class StatusConstants {

    private StatusConstants() {
    }

    // 通用状态
    public static final String NORMAL = "正常";
    public static final String DISABLED = "禁用";

    // 单据状态
    public static final String DRAFT = "草稿";
    public static final String AUDITED = "已审核";
    public static final String COMPLETED = "已完成";

    // 财务状态
    public static final String PAID = "已付款";
    public static final String RECEIVED = "已收款";

    // 业务完成状态
    public static final String PURCHASE_COMPLETED = "完成采购";
    public static final String SALES_COMPLETED = "完成销售";
    public static final String INBOUND_COMPLETED = "完成入库";

    // 签署/送达状态
    public static final String SIGNED = "已签署";
    public static final String DELIVERED = "已送达";

    // 待处理状态
    public static final String PENDING_CONFIRM = "待确认";
    public static final String CONFIRMED = "已确认";
    public static final String PENDING_AUDIT = "待审核";

    public static final Set<String> ALLOWED_ACTIVE_STATUS = Set.of(NORMAL, DISABLED);
    public static final Set<String> ALLOWED_AUDIT_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_RECEIVABLE_STATUS = Set.of(PENDING_CONFIRM, CONFIRMED, PENDING_AUDIT, AUDITED);
}
