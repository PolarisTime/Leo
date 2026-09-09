package com.leo.erp.finance.payment.service;

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
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PaymentService {

    private static final String[] PAYMENT_SEARCH_FIELDS = {
            "paymentNo",
            "businessType",
            "counterpartyName"
    };
    private static final CrudStatusGuard<Payment> STATUS_GUARD = CrudStatusGuard.forStatusAwareEntities();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final SnowflakeIdGenerator idGenerator;
    private final PaymentRepository paymentRepository;
    private final PaymentApplyService applyService;
    private final PaymentMutationGuardService mutationGuardService;
    private final PaymentResponseAssembler responseAssembler;
    private final PaymentSettlementSyncService settlementSyncService;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          PaymentApplyService applyService,
                          PaymentMutationGuardService mutationGuardService,
                          PaymentResponseAssembler responseAssembler,
                          PaymentSettlementSyncService settlementSyncService) {
        this.idGenerator = snowflakeIdGenerator;
        this.paymentRepository = paymentRepository;
        this.applyService = applyService;
        this.mutationGuardService = mutationGuardService;
        this.responseAssembler = responseAssembler;
        this.settlementSyncService = settlementSyncService;
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> page(PageQuery query, PageFilter filter) {
        Specification<Payment> spec = Specs.<Payment>keywordLike(filter.keyword(), "paymentNo", "businessType", "counterpartyName")
                .and(Specs.equalIfPresent("businessType", filter.businessType()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("paymentDate", filter.startDate(), filter.endDate()));
        return pageEntities(query, spec).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> search(String keyword, int maxSize) {
        Specification<Payment> spec = combineSpecifications(
                VISIBILITY_POLICY.applyDeletedVisibility(null, false),
                Specs.keywordLike(keyword, PAYMENT_SEARCH_FIELDS)
        );
        return paymentRepository.findAll(spec, PageRequest.of(0, maxSize))
                .map(this::toResponse)
                .toList();
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

    @Transactional
    public PaymentResponse update(Long id, PaymentRequest request) {
        PaymentResponse updated = updatePayment(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
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
     * 编辑状态守卫 → 更新校验 → 快照当前状态 → 应用请求 →
     * assertRequestStatusTransitionAllowed → allowRequestToWriteFinalStatus 分支下的
     * assertRequestDidNotWriteFinalStatus → 保存。
     */
    private PaymentResponse updatePayment(Long id, PaymentRequest request) {
        Payment entity = requireEntity(id);
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
                request.businessType(),
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
                request.businessType(),
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
        return responseAssembler.toDetailResponse(entity);
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

    protected PaymentResponse toResponse(Payment entity) {
        return responseAssembler.toSummaryResponse(entity);
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

    private Specification<Payment> combineSpecifications(Specification<Payment> left,
                                                         Specification<Payment> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.and(right);
    }

    private long nextId() {
        return idGenerator.nextId();
    }

    private String resolveCreateBusinessNo(Long entityId) {
        if (entityId == null || entityId <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "业务单据雪花ID尚未分配");
        }
        return String.valueOf(entityId);
    }

    private void ensurePaymentNoUnique(String paymentNo) {
        if (paymentRepository.existsByPaymentNoAndDeletedFlagFalse(paymentNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单号已存在");
        }
    }
}
