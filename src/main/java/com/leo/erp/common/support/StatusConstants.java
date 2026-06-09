package com.leo.erp.common.support;

import java.util.Set;

public final class StatusConstants {

    private StatusConstants() {
    }

    // 通用状态
    public static final String NORMAL = "正常";
    public static final String DISABLED = "禁用";
    public static final String DELETED = "已删除";

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

    // 签署状态
    public static final String SIGNED = "已签署";
    public static final String UNSIGNED = "未签署";
    public static final String UNAUDITED = "未审核";
    public static final String ISSUED = "已开票";
    public static final String INVOICE_RECEIVED = "已收票";
    public static final String EXECUTING = "执行中";
    public static final String ARCHIVED = "已归档";

    // 待处理状态
    public static final String PENDING_CONFIRM = "待确认";
    public static final String CONFIRMED = "已确认";
    public static final String PENDING_AUDIT = "待审核";

    public static final Set<String> ALLOWED_ACTIVE_STATUS = Set.of(NORMAL, DISABLED);
    public static final Set<String> ALLOWED_AUDIT_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_RECEIVABLE_STATUS = Set.of(PENDING_CONFIRM, CONFIRMED, PENDING_AUDIT, AUDITED);
    public static final Set<String> ALLOWED_PURCHASE_ORDER_STATUS = Set.of(DRAFT, AUDITED, PURCHASE_COMPLETED);
    public static final Set<String> ALLOWED_PURCHASE_INBOUND_STATUS = Set.of(DRAFT, AUDITED, INBOUND_COMPLETED);
    public static final Set<String> ALLOWED_SALES_ORDER_STATUS = Set.of(DRAFT, AUDITED, SALES_COMPLETED);
    public static final Set<String> ALLOWED_SALES_OUTBOUND_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_CONTRACT_STATUS = Set.of(DRAFT, EXECUTING, SIGNED, ARCHIVED);
    public static final Set<String> ALLOWED_STATEMENT_STATUS = Set.of(PENDING_CONFIRM, CONFIRMED);
    public static final Set<String> ALLOWED_FREIGHT_STATEMENT_STATUS = Set.of(PENDING_AUDIT, AUDITED);
    public static final Set<String> ALLOWED_SIGN_STATUS = Set.of(UNSIGNED, SIGNED);
    public static final Set<String> ALLOWED_FREIGHT_BILL_STATUS = Set.of(UNAUDITED, AUDITED);
    public static final Set<String> ALLOWED_PAYMENT_STATUS = Set.of(DRAFT, PAID);
    public static final Set<String> ALLOWED_RECEIPT_STATUS = Set.of(DRAFT, RECEIVED);
    public static final Set<String> ALLOWED_INVOICE_ISSUE_STATUS = Set.of(DRAFT, ISSUED);
    public static final Set<String> ALLOWED_INVOICE_RECEIPT_STATUS = Set.of(DRAFT, INVOICE_RECEIVED);

    public static final Set<String> PROTECTED_DOCUMENT_STATUS = Set.of(
            AUDITED,
            COMPLETED,
            PURCHASE_COMPLETED,
            INBOUND_COMPLETED,
            SALES_COMPLETED,
            CONFIRMED,
            PAID,
            RECEIVED,
            SIGNED,
            ISSUED,
            INVOICE_RECEIVED,
            ARCHIVED
    );
    public static final Set<String> INVOICEABLE_SALES_ORDER_STATUS = Set.of(AUDITED, SALES_COMPLETED);
    public static final Set<String> INVOICEABLE_PURCHASE_ORDER_STATUS = Set.of(AUDITED, PURCHASE_COMPLETED);
    public static final Set<String> SETTLEABLE_CUSTOMER_STATEMENT_STATUS = Set.of(CONFIRMED);
    public static final Set<String> SETTLEABLE_SUPPLIER_STATEMENT_STATUS = Set.of(CONFIRMED);
    public static final Set<String> SETTLEABLE_FREIGHT_STATEMENT_STATUS = Set.of(AUDITED);

    public static final Set<String> DRAFT_AUDIT_TRANSITIONS = Set.of(
            DRAFT + "->" + AUDITED,
            AUDITED + "->" + DRAFT
    );
    public static final Set<String> CONTRACT_TRANSITIONS = Set.of(
            DRAFT + "->" + EXECUTING,
            EXECUTING + "->" + DRAFT,
            EXECUTING + "->" + SIGNED,
            SIGNED + "->" + EXECUTING,
            SIGNED + "->" + ARCHIVED
    );
    public static final Set<String> STATEMENT_CONFIRM_TRANSITIONS = Set.of(
            PENDING_CONFIRM + "->" + CONFIRMED,
            CONFIRMED + "->" + PENDING_CONFIRM
    );
    public static final Set<String> FREIGHT_BILL_AUDIT_TRANSITIONS = Set.of(
            UNAUDITED + "->" + AUDITED,
            AUDITED + "->" + UNAUDITED
    );
}
