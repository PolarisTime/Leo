package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 单据/业务状态的稳定英文 code 常量表。
 *
 * <p><b>唯一口径</b>：V219 之后数据库、API 响应、查询参数与代码内部比较一律使用这里的 code；
 * 中文 label 只出现在前端展示与旧数据/旧客户端兼容路径。{@link #toCode(String)} 负责把
 * 旧中文 label（正常/草稿/已审核/…）与 code 统一归一化为 code，供写接口与查询参数复用。</p>
 *
 * <p>新增状态时必须同步：{@link DocumentStatus}（如有 label 映射）、本类常量、DB 迁移脚本
 * 与前端词表；禁止在业务代码里再写中文状态字面量。</p>
 */
public final class StatusConstants {

    private StatusConstants() {
    }

    // 通用状态
    public static final String NORMAL = DocumentStatus.NORMAL.code();
    public static final String DISABLED = DocumentStatus.DISABLED.code();

    // 单据状态
    public static final String DRAFT = DocumentStatus.DRAFT.code();
    public static final String AUDITED = DocumentStatus.AUDITED.code();
    /** 销售合同审核态（旧 label 为「审核」）: 与通用 {@link #AUDITED}（旧 label「已审核」）区分。 */
    public static final String CONTRACT_REVIEWED = DocumentStatus.CONTRACT_REVIEWED.code();
    /** 销售合同签发态（旧 label 为「签发」）: 区别于通用 {@link #AUDITED}。 */
    public static final String CONTRACT_ISSUED = DocumentStatus.CONTRACT_ISSUED.code();
    public static final String COMPLETED = DocumentStatus.COMPLETED.code();
    /** 销售合同作废: 终态只读, 不计入有效合同额度, 不能从作废回到审核。 */
    public static final String VOIDED = DocumentStatus.VOIDED.code();

    // 财务状态
    /** 历史付款状态 code（迁移前旧 label 为「已付款」）。 */
    public static final String PAID = "PAID";
    /** 历史收款状态 code（迁移前旧 label 为「已收款」）。 */
    public static final String RECEIVED = "RECEIVED";
    /**
     * 历史付款/收款状态 code 的兼容别名。
     *
     * <p>这两个值来自迁移前的财务流程，新流程统一写 {@link #AUDITED}；V219 已把存量数据
     * 迁成 {@link #PAID}/{@link #RECEIVED}，故这里只是旧命名的别名，用于守卫历史单据：
     * {@code PaymentMutationGuardService} 与 {@code ReceiptService} 仍需拒绝其编辑/删除。</p>
     */
    public static final String LEGACY_PAID = PAID;
    /** 见 {@link #LEGACY_PAID}。 */
    public static final String LEGACY_RECEIVED = RECEIVED;

    // 业务完成状态
    public static final String PURCHASE_COMPLETED = DocumentStatus.PURCHASE_COMPLETED.code();
    public static final String SALES_COMPLETED = DocumentStatus.SALES_COMPLETED.code();
    public static final String INBOUND_COMPLETED = DocumentStatus.INBOUND_COMPLETED.code();
    public static final String DELIVERY_VERIFICATION = DocumentStatus.DELIVERY_VERIFICATION.code();

    // 签署状态
    public static final String SIGNED = DocumentStatus.SIGNED.code();
    public static final String UNSIGNED = DocumentStatus.UNSIGNED.code();
    public static final String UNAUDITED = DocumentStatus.UNAUDITED.code();
    public static final String EXECUTING = DocumentStatus.EXECUTING.code();
    public static final String ARCHIVED = DocumentStatus.ARCHIVED.code();

    // 待处理状态
    public static final String PENDING_CONFIRM = DocumentStatus.PENDING_CONFIRM.code();
    public static final String CONFIRMED = DocumentStatus.CONFIRMED.code();
    public static final String PENDING_AUDIT = DocumentStatus.PENDING_AUDIT.code();

    /** 报价单状态 code（报价单是独立状态维度，不属于 {@link DocumentStatus}）。 */
    public static final String QUOTING = "QUOTING";
    /** 操作日志结果 code（与 {@code OperationLogConstants.RESULT_*} 同一取值）。 */
    public static final String SUCCESS = "SUCCESS";
    /** 操作日志结果 code。 */
    public static final String FAILED = "FAILED";
    /** 历史开票单终态 code（仅 fm_invoice_issue 历史数据/约束使用，无 Java 服务引用）。 */
    public static final String INVOICE_ISSUED = "INVOICE_ISSUED";
    /** 历史收票单终态 code（仅 fm_invoice_receipt 历史数据/约束使用，无 Java 服务引用）。 */
    public static final String INVOICE_RECEIVED = "INVOICE_RECEIVED";

    /**
     * 旧中文状态 label 的归一化映射：只用于兼容 V219 之前的旧数据与旧客户端，
     * 覆盖不属于 {@link DocumentStatus} 的报价/操作日志/历史财务状态。
     */
    private static final Map<String, String> LEGACY_LABEL_CODES = Map.of(
            "报价", QUOTING,
            "成功", SUCCESS,
            "失败", FAILED,
            "已付款", PAID,
            "已收款", RECEIVED,
            // 历史合同/开票单的旧取值（V219 映射表同源，应用暂无服务读取这些单据）
            "已归档", ARCHIVED,
            "已开票", INVOICE_ISSUED,
            "已收票", INVOICE_RECEIVED,
            // 结算账户明细 JSON（sys_company_setting.settlement_accounts）的旧停用值，
            // 该 JSON 不在 V219 的 37 张表范围内，读取时按同一 code 口径归一化。
            "停用", DISABLED
    );

    /**
     * 把状态入参归一化为稳定英文 code（写接口、查询参数、JSON 反序列化共用）。
     *
     * <p>接受三类输入：已迁移的旧中文 label（如「草稿」→ {@code DRAFT}）、旧财务/日志/报价
     * label（如「已付款」→ {@code PAID}）、以及已经是 code 的值（原样返回）；
     * 未知值同样原样返回，由调用方的白名单校验决定是否拒绝。空值/空白返回 {@code null}。</p>
     */
    public static String toCode(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Optional<DocumentStatus> documentStatus = DocumentStatus.fromLabel(trimmed);
        if (documentStatus.isPresent()) {
            return documentStatus.get().code();
        }
        String legacyCode = LEGACY_LABEL_CODES.get(trimmed);
        return legacyCode != null ? legacyCode : trimmed;
    }

    /** 全部单据/业务状态稳定 code，由 {@link DocumentStatus} 派生，便于统一校验与断言。 */
    public static Set<String> codes() {
        return DocumentStatus.codes();
    }

    /**
     * 全部单据/业务状态旧中文 label，由 {@link DocumentStatus} 派生。
     *
     * @deprecated DB 与 API 已统一为 code；仅旧数据兼容/展示与迁移期断言使用，请改用 {@link #codes()}。
     */
    @Deprecated
    public static Set<String> labels() {
        return DocumentStatus.labels();
    }

    public static final Set<String> ALLOWED_ACTIVE_STATUS = Set.of(NORMAL, DISABLED);
    public static final Set<String> ALLOWED_AUDIT_STATUS = Set.of(DRAFT, AUDITED);
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
    /**
     * 参与合同金额/吨位累计的销售合同状态: 审核 / 签发 / 归档。
     * 草稿与作废不计入有效合同额度。
     */
    public static final Set<String> SALES_CONTRACT_QUOTA_STATUSES =
            Set.of(CONTRACT_REVIEWED, CONTRACT_ISSUED, ARCHIVED);
    public static final Set<String> ALLOWED_STATEMENT_STATUS = Set.of(PENDING_CONFIRM, CONFIRMED);

    // 客户对账单方向：蓝字=正常对账（正数），红字=退货冲销（负数）
    public static final String STATEMENT_DIRECTION_BLUE = "蓝字";
    public static final String STATEMENT_DIRECTION_RED = "红字";
    public static final Set<String> ALLOWED_STATEMENT_DIRECTION = Set.of(
            STATEMENT_DIRECTION_BLUE,
            STATEMENT_DIRECTION_RED
    );
    public static final Set<String> ALLOWED_FREIGHT_STATEMENT_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_FREIGHT_BILL_STATUS = Set.of(DRAFT, AUDITED);
    /**
     * 「物流单从销售订单导入」候选池允许的状态：已审核 / 送货核验 / 销售完成。
     *
     * <p>候选池状态的**唯一来源**：{@code FreightBillSalesOrderCandidateQueryRepository} 用它拼
     * {@code status IN (...)}，控制器用 {@link #normalizeOptionalSalesOrderCandidateStatus} 据此校验入参。
     * 草稿态订单不能作为物流单来源，故不在池内。</p>
     */
    public static final Set<String> ALLOWED_SALES_ORDER_CANDIDATE_STATUS = Set.of(
            AUDITED,
            DELIVERY_VERIFICATION,
            SALES_COMPLETED
    );
    /** {@link #ALLOWED_SALES_ORDER_CANDIDATE_STATUS} 的稳定展示顺序(用于报错文案, 避免 Set 迭代顺序漂移)。 */
    public static final List<String> ALLOWED_SALES_ORDER_CANDIDATE_STATUS_CODES = List.of(
            AUDITED,
            DELIVERY_VERIFICATION,
            SALES_COMPLETED
    );
    public static final Set<String> ALLOWED_PAYMENT_STATUS = Set.of(DRAFT, AUDITED);
    public static final Set<String> ALLOWED_RECEIPT_STATUS = Set.of(DRAFT, AUDITED);

    /**
     * 受保护单据状态: 编辑与删除均被拒绝, 只能通过专用状态接口流转。
     * 其中 {@link #CONTRACT_REVIEWED}、{@link #CONTRACT_ISSUED} 与 {@link #VOIDED} 服务于
     * 销售合同「审核/签发/归档/作废不可编辑、不可删除, 仅草稿可编辑可删除」的口径;
     * {@code 作废} 为终态只读, 不可再流转。
     */
    public static final Set<String> PROTECTED_DOCUMENT_STATUS = Set.of(
            AUDITED,
            COMPLETED,
            PURCHASE_COMPLETED,
            INBOUND_COMPLETED,
            DELIVERY_VERIFICATION,
            SALES_COMPLETED,
            CONFIRMED,
            SIGNED,
            ARCHIVED,
            CONTRACT_REVIEWED,
            CONTRACT_ISSUED,
            VOIDED
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
    /**
     * 销售合同状态迁移(定稿): 草稿 → 审核 → 签发 → 归档, 不支持逆向回退;
     * 作废仅允许自 草稿 / 审核 / 归档, 签发不可作废(须先归档); 作废为终态, 无出边。
     */
    public static final Set<StatusTransition> SALES_CONTRACT_TRANSITIONS = Set.of(
            StatusTransition.of(DRAFT, CONTRACT_REVIEWED),
            StatusTransition.of(CONTRACT_REVIEWED, CONTRACT_ISSUED),
            StatusTransition.of(CONTRACT_ISSUED, ARCHIVED),
            StatusTransition.of(DRAFT, VOIDED),
            StatusTransition.of(CONTRACT_REVIEWED, VOIDED),
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

    /**
     * 归一化「物流单销售订单候选」的 status 过滤参数。
     *
     * <p>空/空白视为「前端已 opt-out 该筛选项」→ 返回 {@code null}（不额外过滤）；
     * 非空则必须落在 {@link #ALLOWED_SALES_ORDER_CANDIDATE_STATUS} 内，否则抛
     * {@link ErrorCode#VALIDATION_ERROR}（HTTP 422）。</p>
     *
     * <p>为什么池外状态要报错而不是静默返回 0 条：候选池本就限定三态，传「草稿」在语义上
     * 是无效查询；静默空结果会让前端以为「该状态下没有候选」，把参数错误伪装成业务事实。</p>
     */
    public static String normalizeOptionalSalesOrderCandidateStatus(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_SALES_ORDER_CANDIDATE_STATUS.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    fieldName + "不合法: " + normalized
                            + "（候选仅支持 " + String.join(" / ", ALLOWED_SALES_ORDER_CANDIDATE_STATUS_CODES) + "）"
            );
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

    /** 归一化为稳定 code：接受旧中文 label 与 code，空值/空白返回 {@code null}。 */
    private static String normalizeOptional(String value) {
        return toCode(value);
    }
}
