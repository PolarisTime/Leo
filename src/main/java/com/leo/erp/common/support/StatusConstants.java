package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Set;

public final class StatusConstants {

    private StatusConstants() {
    }

    // 通用状态
    public static final String NORMAL = DocumentStatus.NORMAL.label();
    public static final String DISABLED = DocumentStatus.DISABLED.label();

    // 单据状态
    public static final String DRAFT = DocumentStatus.DRAFT.label();
    /** 仅用于识别历史数据，新流程不得写入。 */
    @Deprecated
    public static final String PRE_OUTBOUND = DocumentStatus.PRE_OUTBOUND.label();
    public static final String AUDITED = DocumentStatus.AUDITED.label();
    /** 销售合同已发出: 已审核之后发出, 归归档前置, 不可直接作废。 */
    public static final String ISSUED = DocumentStatus.ISSUED.label();
    public static final String COMPLETED = DocumentStatus.COMPLETED.label();
    /** 销售合同作废: 不计入有效合同额度, 不能从作废回到已审核。 */
    public static final String VOIDED = DocumentStatus.VOIDED.label();

    // 财务状态
    /** 仅用于兼容迁移前的付款数据，新流程统一使用 {@link #AUDITED}。 */
    @Deprecated
    public static final String PAID = DocumentStatus.LEGACY_PAID.label();
    /** 仅用于兼容迁移前的收款数据，新流程统一使用 {@link #AUDITED}。 */
    @Deprecated
    public static final String RECEIVED = DocumentStatus.LEGACY_RECEIVED.label();
    public static final String LEGACY_PAID = PAID;
    public static final String LEGACY_RECEIVED = RECEIVED;

    // 业务完成状态
    public static final String PURCHASE_COMPLETED = DocumentStatus.PURCHASE_COMPLETED.label();
    public static final String SALES_COMPLETED = DocumentStatus.SALES_COMPLETED.label();
    public static final String INBOUND_COMPLETED = DocumentStatus.INBOUND_COMPLETED.label();
    public static final String DELIVERY_VERIFICATION = DocumentStatus.DELIVERY_VERIFICATION.label();

    // 签署状态
    public static final String SIGNED = DocumentStatus.SIGNED.label();
    public static final String UNSIGNED = DocumentStatus.UNSIGNED.label();
    public static final String UNAUDITED = DocumentStatus.UNAUDITED.label();
    public static final String EXECUTING = DocumentStatus.EXECUTING.label();
    public static final String ARCHIVED = DocumentStatus.ARCHIVED.label();

    // 待处理状态
    public static final String PENDING_CONFIRM = DocumentStatus.PENDING_CONFIRM.label();
    public static final String CONFIRMED = DocumentStatus.CONFIRMED.label();
    public static final String PENDING_AUDIT = DocumentStatus.PENDING_AUDIT.label();

    /**
     * 全部单据/业务状态中文值，由 {@link DocumentStatus} 派生，便于统一校验与断言。
     */
    public static Set<String> labels() {
        return DocumentStatus.labels();
    }

    public static final Set<String> ALLOWED_ACTIVE_STATUS = Set.of(NORMAL, DISABLED);
    public static final Set<String> ALLOWED_AUDIT_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_RECEIVABLE_STATUS = Set.of(PENDING_CONFIRM, CONFIRMED, PENDING_AUDIT, AUDITED);
    public static final Set<String> ALLOWED_PURCHASE_ORDER_STATUS = Set.of(DRAFT, AUDITED, PURCHASE_COMPLETED);
    public static final Set<String> ALLOWED_PURCHASE_INBOUND_STATUS = Set.of(DRAFT, AUDITED, INBOUND_COMPLETED);
    public static final Set<String> ALLOWED_SALES_ORDER_STATUS = Set.of(
            DRAFT,
            AUDITED,
            DELIVERY_VERIFICATION,
            SALES_COMPLETED
    );
    public static final Set<String> ALLOWED_SALES_OUTBOUND_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_SALES_RETURN_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_CONTRACT_STATUS = Set.of(DRAFT, EXECUTING, SIGNED, ARCHIVED);
    /** 销售合同合法状态集合: 草稿 / 已审核 / 已发出 / 归档 / 作废。 */
    public static final Set<String> ALLOWED_SALES_CONTRACT_STATUS =
            Set.of(DRAFT, AUDITED, ISSUED, ARCHIVED, VOIDED);
    /**
     * 参与合同金额/吨位累计的销售合同状态: 已审核 / 已发出 / 归档。
     * 草稿与作废不计入有效合同额度。
     */
    public static final Set<String> SALES_CONTRACT_QUOTA_STATUSES = Set.of(AUDITED, ISSUED, ARCHIVED);
    public static final Set<String> ALLOWED_STATEMENT_STATUS = Set.of(PENDING_CONFIRM, CONFIRMED);

    // 客户对账单方向：蓝字=正常对账（正数），红字=退货冲销（负数）
    public static final String STATEMENT_DIRECTION_BLUE = "蓝字";
    public static final String STATEMENT_DIRECTION_RED = "红字";
    public static final Set<String> ALLOWED_STATEMENT_DIRECTION = Set.of(
            STATEMENT_DIRECTION_BLUE,
            STATEMENT_DIRECTION_RED
    );
    public static final Set<String> ALLOWED_FREIGHT_STATEMENT_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_SIGN_STATUS = Set.of(UNSIGNED, SIGNED);
    public static final Set<String> ALLOWED_FREIGHT_BILL_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_PAYMENT_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_RECEIPT_STATUS = Set.of(DRAFT, AUDITED);

    public static final Set<String> PROTECTED_DOCUMENT_STATUS = Set.of(
            AUDITED,
            COMPLETED,
            PURCHASE_COMPLETED,
            INBOUND_COMPLETED,
            DELIVERY_VERIFICATION,
            SALES_COMPLETED,
            CONFIRMED,
            SIGNED,
            ISSUED,
            ARCHIVED
    );
    public static final Set<String> SETTLEABLE_CUSTOMER_STATEMENT_STATUS = Set.of(CONFIRMED);
    public static final Set<String> SETTLEABLE_FREIGHT_STATEMENT_STATUS = Set.of(AUDITED);

    public static final Set<StatusTransition> DRAFT_AUDIT_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, DRAFT)
    );
    public static final Set<StatusTransition> DRAFT_TO_AUDITED_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED)
    );
    public static final Set<StatusTransition> PURCHASE_ORDER_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, DRAFT),
            StatusTransition.of(PURCHASE_COMPLETED, AUDITED)
    );
    public static final Set<StatusTransition> PURCHASE_INBOUND_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, DRAFT),
            StatusTransition.of(INBOUND_COMPLETED, DRAFT)
    );
    public static final Set<StatusTransition> SALES_OUTBOUND_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, DRAFT)
    );
    public static final Set<StatusTransition> SALES_RETURN_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, DRAFT)
    );
    public static final Set<StatusTransition> SALES_ORDER_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, DRAFT),
            StatusTransition.of(DELIVERY_VERIFICATION, SALES_COMPLETED),
            StatusTransition.of(SALES_COMPLETED, DELIVERY_VERIFICATION)
    );
    public static final Set<StatusTransition> CONTRACT_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, EXECUTING),
            StatusTransition.of(EXECUTING, DRAFT),
            StatusTransition.of(EXECUTING, SIGNED),
            StatusTransition.of(SIGNED, EXECUTING),
            StatusTransition.of(SIGNED, ARCHIVED)
    );
    /**
     * 销售合同状态迁移(定稿): 草稿 → 已审核 → 已发出 → 归档, 不支持逆向回退;
     * 作废仅允许自 草稿 / 已审核 / 归档, 已发出必须先归档再作废。
     */
    public static final Set<StatusTransition> SALES_CONTRACT_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, ISSUED),
            StatusTransition.of(ISSUED, ARCHIVED),
            StatusTransition.of(DRAFT, VOIDED),
            StatusTransition.of(AUDITED, VOIDED),
            StatusTransition.of(ARCHIVED, VOIDED)
    );
    public static final Set<StatusTransition> STATEMENT_CONFIRM_TRANSITIONS = Set.of(
            StatusTransition.of(PENDING_CONFIRM, CONFIRMED),
            StatusTransition.of(CONFIRMED, PENDING_CONFIRM)
    );
    public static final Set<StatusTransition> FREIGHT_BILL_AUDIT_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, AUDITED),
            StatusTransition.of(AUDITED, DRAFT)
    );

    public static String normalizeActiveStatus(String value, String fieldName) {
        String normalized = normalizeRequired(value, fieldName);
        if (!ALLOWED_ACTIVE_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不合法");
        }
        return normalized;
    }

    public static String normalizeOptionalActiveStatus(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_ACTIVE_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不合法");
        }
        return normalized;
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不能为空");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
