package com.leo.erp.finance.payment.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractStatusCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentService extends AbstractStatusCrudService<Payment, PaymentRequest, PaymentResponse> {

    private static final String[] PAYMENT_SEARCH_FIELDS = {
            "paymentNo",
            "businessType",
            "counterpartyName"
    };

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
        super(snowflakeIdGenerator);
        this.paymentRepository = paymentRepository;
        this.applyService = applyService;
        this.mutationGuardService = mutationGuardService;
        this.responseAssembler = responseAssembler;
        this.settlementSyncService = settlementSyncService;
    }

    public Page<PaymentResponse> page(PageQuery query, PageFilter filter) {
        Specification<Payment> spec = Specs.<Payment>keywordLike(filter.keyword(), "paymentNo", "businessType", "counterpartyName")
                .and(Specs.equalIfPresent("businessType", filter.businessType()))
                .and(Specs.documentStatus(filter.status()))
                .and(Specs.betweenIfPresent("paymentDate", filter.startDate(), filter.endDate()));
        return page(query, spec, paymentRepository);
    }

    public List<PaymentResponse> search(String keyword, int maxSize) {
        return search(keyword, PAYMENT_SEARCH_FIELDS, maxSize, null, paymentRepository);
    }

    @Override
    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        PaymentResponse created = super.create(
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(created.id(), StatusConstants.AUDITED);
        }
        return created;
    }

    @Override
    @Transactional
    public PaymentResponse update(Long id, PaymentRequest request) {
        PaymentResponse updated = super.update(id,
                request.audit() ? withStatus(request, StatusConstants.DRAFT) : request);
        if (request.audit()) {
            return updateStatus(id, StatusConstants.AUDITED);
        }
        return updated;
    }

    @Override
    @Transactional
    public PaymentResponse updateStatus(Long id, String status) {
        mutationGuardService.lockRoot(id);
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        mutationGuardService.lockRoot(id);
        super.delete(id);
    }

    @Override
    protected void validateCreate(PaymentRequest request) {
        ensurePaymentNoUnique(request.paymentNo());
    }

    @Override
    protected void validateUpdate(Payment entity, PaymentRequest request) {
        mutationGuardService.assertUpdateAllowed(entity, "修改");
        if (!entity.getPaymentNo().equals(request.paymentNo())) {
            ensurePaymentNoUnique(request.paymentNo());
        }
    }

    @Override
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

    @Override
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

    @Override
    protected Payment newEntity() {
        return new Payment();
    }

    @Override
    protected void assignId(Payment entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Payment> findActiveEntity(Long id) {
        return paymentRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<Payment> findVisibleEntity(Long id) {
        return paymentRepository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "付款单不存在";
    }

    @Override
    protected boolean allowViewingDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<StatusTransition> allowedStatusTransitions() {
        return StatusConstants.DRAFT_TO_AUDITED_TRANSITIONS;
    }

    @Override
    protected void beforeStatusUpdate(Payment entity, String currentStatus, String nextStatus) {
        mutationGuardService.assertStatusTransitionAllowed(entity, currentStatus, nextStatus);
    }

    @Override
    protected void beforeDelete(Payment entity) {
        mutationGuardService.assertDeletable(entity);
    }

    @Override
    protected PaymentResponse toDetailResponse(Payment entity) {
        return responseAssembler.toDetailResponse(entity);
    }

    @Override
    protected PaymentResponse toSavedResponse(Payment entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void apply(Payment entity, PaymentRequest request) {
        mutationGuardService.lockAllocationStatements(entity, request);
        applyService.apply(entity, request, this::nextId);
    }

    @Override
    protected Payment saveEntity(Payment entity) {
        Payment saved = paymentRepository.save(entity);
        settlementSyncService.syncLinkedStatements(saved);
        return saved;
    }

    @Override
    protected PaymentResponse toResponse(Payment entity) {
        return responseAssembler.toSummaryResponse(entity);
    }

    private void ensurePaymentNoUnique(String paymentNo) {
        if (paymentRepository.existsByPaymentNoAndDeletedFlagFalse(paymentNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单号已存在");
        }
    }
}
