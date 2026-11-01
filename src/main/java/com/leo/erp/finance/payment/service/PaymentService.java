package com.leo.erp.finance.payment.service;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.security.permission.StatusTransitionPermissionGuard;
import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.common.web.ResourceVersionPrecondition;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.system.company.api.SettlementAccountSnapshot;
import com.leo.erp.system.company.api.CompanySettingQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final CrudStatusGuard<Payment> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final PaymentRepository paymentRepository;
    private final PaymentApplyService applyService;
    private final PaymentMutationGuardService mutationGuardService;
    private final PaymentResponseAssembler responseAssembler;
    private final PaymentSettlementSyncService settlementSyncService;
    private final CompanySettingQuery companySettingService;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          PaymentApplyService applyService,
                          PaymentMutationGuardService mutationGuardService,
                          PaymentResponseAssembler responseAssembler,
                          PaymentSettlementSyncService settlementSyncService,
                          CompanySettingQuery companySettingService) {
        this.idGenerator = snowflakeIdGenerator;
        this.paymentRepository = paymentRepository;
        this.applyService = applyService;
        this.mutationGuardService = mutationGuardService;
        this.responseAssembler = responseAssembler;
        this.settlementSyncService = settlementSyncService;
        this.companySettingService = companySettingService;
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> page(PageQuery query, PageFilter filter) {
        Specification<Payment> spec = Specs.<Payment>keywordLike(filter.keyword(), "paymentNo", "counterpartyType", "counterpartyName")
                .and(Specs.equalIfPresent("counterpartyType", filter.businessType()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("paymentDate", filter.startDate(), filter.endDate()));
        Page<Payment> entities = pageEntities(query, spec);
        // 资金账户快照按整页批量解析（一次查询），不能放到逐行的 toResponse 里，否则分页会出现 N+1。
        Map<Long, SettlementAccountSnapshot> accounts = settlementAccountIndex(entities.getContent());
        return entities.map(entity -> responseAssembler.toSummaryResponse(entity,
                account(accounts, entity.getAccountId())));
    }

    @Transactional(readOnly = true)
    public PaymentResponse detail(Long id) {
        return toDetailResponse(requireDetailEntity(id));
    }

    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        PaymentResponse created = createPayment(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    /**
     * 整体替换写路径（{@code PUT /api/v2.0/payments/{id}}）。
     *
     * <p><b>版本前置条件：</b>{@code expectedVersion} 来自 {@code X-Resource-Version}（兼容
     * {@code If-Match}），与实体 {@code @Version} 比对：不一致抛 412
     * （{@link ErrorCode#PRECONDITION_FAILED}），避免「A 打开表单 → B 保存 → A 保存」静默覆盖。
     * 缺少版本头返回 428 由入口（控制器）负责。</p>
     *
     * <p><b>响应必须带最终版本：</b>收尾统一走 {@link #refreshedDetail}（flush 后重新装配）——
     * {@code @Version} 在 flush 时才自增，返回中途快照会让客户端拿到旧版本，下一次保存必然 412。</p>
     */
    @Transactional
    public PaymentResponse update(Long id, PaymentRequest request, Long expectedVersion) {
        updatePayment(id, request.audit() ? withStatus(request, StatusConstants.DRAFT) : request,
                expectedVersion);
        if (request.audit()) {
            updateStatus(id, StatusConstants.AUDITED);
        }
        return refreshedDetail(id);
    }

    @Transactional
    public PaymentResponse updateStatus(Long id, String status) {
        mutationGuardService.lockRoot(id);
        return doUpdateStatus(id, status);
    }

    @Transactional
    public void delete(Long id) {
        mutationGuardService.lockRoot(id);
        Payment entity = requireEntity(id);
        STATUS_GUARD.assertDeleteAllowed(entity);
        beforeDelete(entity);
        entity.setDeletedFlag(true);
        saveEntity(entity);
        log.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }

    /**
     * 基类 create 的显式内联：雪花 ID → 归一化 → 校验 → 应用 → 终态双写守卫 → 保存。
     */
    private PaymentResponse createPayment(PaymentRequest request) {
        Payment entity = newEntity();
        long entityId = idGenerator.nextId();
        assignId(entity, entityId);
        PaymentRequest normalized = normalizeCreateRequest(request, entityId);
        validateCreate(normalized);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        PaymentResponse response = toSavedResponse(saveCreatedEntity(entity, normalized));
        log.info("{} created: id={}", entity.getClass().getSimpleName(), entityId);
        return response;
    }

    /**
     * 基类 update 的显式内联，状态断言序列逐字保持：
     * 版本前置条件 → 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     *
     * <p>版本校验紧跟在「加载实体」之后、任何业务校验之前：前置条件的语义是
     * 「你编辑的这一版是否仍然是最新版」，它先于字段合法性判断才有意义；
     * 实体不存在仍先由 {@link #requireEntity} 返回 404。</p>
     */
    private PaymentResponse updatePayment(Long id, PaymentRequest request, Long expectedVersion) {
        Payment entity = requireEntity(id);
        ResourceVersionPrecondition.verify(expectedVersion, entity.getVersion(), "付款单");
        PaymentRequest normalized = normalizeUpdateRequest(entity, request);
        // 基类 allowProtectedStatusUpdate 默认 false：受保护状态单据不允许普通编辑。
        STATUS_GUARD.assertEditAllowed(entity, false);
        validateUpdate(entity, normalized);
        Optional<String> currentStatus = STATUS_GUARD.resolveStatus(entity);
        apply(entity, normalized);
        STATUS_GUARD.assertRequestStatusTransitionAllowed(entity, currentStatus, allowedStatusTransitions());
        // 基类 allowRequestToWriteFinalStatus 默认 false：普通保存一律拒绝终态写入。
        STATUS_GUARD.assertRequestDidNotWriteFinalStatus(entity);
        PaymentResponse response = toSavedResponse(saveUpdatedEntity(entity, normalized));
        log.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
        return response;
    }

    /**
     * 基类 updateStatus 的显式内联：等值短路 → 迁移表校验 → beforeStatusUpdate → 写状态 → 状态保存。
     */
    private PaymentResponse doUpdateStatus(Long id, String status) {
        Payment entity = requireEntity(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toSavedResponse(entity);
        }
        StatusTransitionPermissionGuard.requireForTransition(ModuleKeys.PAYMENT, currentStatus, nextStatus);
        STATUS_GUARD.validateStatusTransition(allowedStatusTransitions(), currentStatus, nextStatus);
        beforeStatusUpdate(entity, currentStatus, nextStatus);
        STATUS_GUARD.writeStatus(entity, nextStatus);
        PaymentResponse response = toSavedResponse(saveEntity(entity));
        log.info(
                "{} status updated: id={}, {} -> {}",
                entity.getClass().getSimpleName(),
                id,
                currentStatus,
                nextStatus
        );
        return response;
    }

    protected void validateCreate(PaymentRequest request) {
        ensurePaymentNoUnique(request.paymentNo());
    }

    protected void validateUpdate(Payment entity, PaymentRequest request) {
        mutationGuardService.assertUpdateAllowed(entity, "修改");
        if (!entity.getPaymentNo().equals(request.paymentNo())) {
            ensurePaymentNoUnique(request.paymentNo());
        }
    }

    protected PaymentRequest normalizeCreateRequest(PaymentRequest request, long entityId) {
        return new PaymentRequest(
                resolveCreateBusinessNo(entityId),
                request.counterpartyType(),
                request.counterpartyId(),
                request.paymentPurpose(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.sourceStatementId(),
                request.sourcePurchaseOrderId(),
                request.purchaseOrderNo(),
                request.supplierCode(),
                request.supplierName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.accountId(),
                request.paymentDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private PaymentRequest withStatus(PaymentRequest request, String status) {
        return new PaymentRequest(
                request.paymentNo(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.paymentPurpose(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.sourceStatementId(),
                request.sourcePurchaseOrderId(),
                request.purchaseOrderNo(),
                request.supplierCode(),
                request.supplierName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.accountId(),
                request.paymentDate(),
                request.payType(),
                request.amount(),
                status,
                request.operatorName(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    protected PaymentRequest normalizeUpdateRequest(Payment entity, PaymentRequest request) {
        return new PaymentRequest(
                entity.getPaymentNo(),
                request.counterpartyType(),
                request.counterpartyId(),
                request.paymentPurpose(),
                request.counterpartyCode(),
                request.counterpartyName(),
                request.sourceStatementId(),
                request.sourcePurchaseOrderId(),
                request.purchaseOrderNo(),
                request.supplierCode(),
                request.supplierName(),
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                request.accountId(),
                request.paymentDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    protected Payment newEntity() {
        return new Payment();
    }

    protected void assignId(Payment entity, Long id) {
        entity.setId(id);
    }

    protected Optional<Payment> findActiveEntity(Long id) {
        return paymentRepository.findByIdAndDeletedFlagFalse(id);
    }

    protected Optional<Payment> findVisibleEntity(Long id) {
        return paymentRepository.findById(id);
    }

    protected String notFoundMessage() {
        return "付款单不存在";
    }

    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    protected Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.DRAFT_TO_AUDITED_TRANSITIONS;
    }

    protected void beforeStatusUpdate(Payment entity, String currentStatus, String nextStatus) {
        mutationGuardService.assertStatusTransitionAllowed(entity, currentStatus, nextStatus);
    }

    protected void beforeDelete(Payment entity) {
        mutationGuardService.assertDeletable(entity);
    }

    protected PaymentResponse toDetailResponse(Payment entity) {
        return responseAssembler.toDetailResponse(entity, settlementAccount(entity));
    }

    protected PaymentResponse toSavedResponse(Payment entity) {
        return toDetailResponse(entity);
    }

    protected void apply(Payment entity, PaymentRequest request) {
        mutationGuardService.lockAllocationStatements(entity, request);
        applyService.apply(entity, request, this::nextId);
    }

    protected Payment saveEntity(Payment entity) {
        Payment saved = paymentRepository.save(entity);
        settlementSyncService.syncLinkedStatements(saved);
        return saved;
    }

    protected Payment saveCreatedEntity(Payment entity, PaymentRequest request) {
        return saveEntity(entity);
    }

    protected Payment saveUpdatedEntity(Payment entity, PaymentRequest request) {
        return saveEntity(entity);
    }

    /**
     * 写路径收尾：先 flush 把 {@code @Version} 推进到本事务的最终值，再重新装配详情。
     *
     * <p><b>为什么必须 flush：</b>{@code @Version} 在 flush 时才自增。整体替换写路径里
     * 「保存单据 → 同步关联对账单 → 状态迁移（audit 分支）」会连续写同一实体，
     * 直接返回中途装配的响应会让客户端拿到**旧版本号**，下一次保存必然 412。
     * 重新装配而不是只改版本号，是为了让响应里其它字段与被推进的版本自洽。</p>
     */
    private PaymentResponse refreshedDetail(Long id) {
        paymentRepository.flush();
        return toDetailResponse(requireEntity(id));
    }

    protected PaymentResponse toResponse(Payment entity) {
        return responseAssembler.toSummaryResponse(entity, settlementAccount(entity));
    }

    /** 按整批单据的结算主体一次性解析资金账户快照，返回 accountId -> 快照。 */
    private Map<Long, SettlementAccountSnapshot> settlementAccountIndex(List<Payment> payments) {
        return companySettingService.settlementAccountSnapshots(payments.stream()
                .map(Payment::getSettlementCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }

    private SettlementAccountSnapshot settlementAccount(Payment entity) {
        return companySettingService.settlementAccountSnapshot(
                entity.getSettlementCompanyId(), entity.getAccountId());
    }

    /** 空 Map 实现不接受 null key，必须先判空再取快照。 */
    private SettlementAccountSnapshot account(Map<Long, SettlementAccountSnapshot> accounts, Long accountId) {
        return accountId == null ? null : accounts.get(accountId);
    }

    private Payment requireEntity(Long id) {
        return findActiveEntity(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private Payment requireDetailEntity(Long id) {
        if (allowViewingDeletedRecords()) {
            return findVisibleEntity(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
        }
        return requireEntity(id);
    }

    private Page<Payment> pageEntities(PageQuery query, Specification<Payment> specification) {
        Specification<Payment> effectiveSpec =
                VISIBILITY_POLICY.applyDeletedVisibility(specification, allowViewingDeletedRecords());
        return paymentRepository.findAll(effectiveSpec, query.toPageable("id"));
    }

    private long nextId() {
        return idGenerator.nextId();
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.SNOWFLAKE_ID_NOT_ASSIGNED);
        }
        return String.valueOf(entityId);
    }

    private void ensurePaymentNoUnique(String paymentNo) {
        if (paymentRepository.existsByPaymentNoAndDeletedFlagFalse(paymentNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单号已存在");
        }
    }
}
