package com.leo.erp.finance.cashreversal.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.cashreversal.domain.entity.CashReversal;
import com.leo.erp.finance.cashreversal.mapper.CashReversalMapper;
import com.leo.erp.finance.cashreversal.repository.CashReversalRepository;
import com.leo.erp.finance.cashreversal.web.dto.CashReversalRequest;
import com.leo.erp.finance.cashreversal.web.dto.CashReversalResponse;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.purchaseflow.service.SupplierLedgerLockService;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.repository.ReceiptRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CashReversalService extends AbstractCrudService<
        CashReversal, CashReversalRequest, CashReversalResponse> {

    private static final String MODULE_KEY = "cash-reversal";
    private static final String SUPPLIER = "供应商";
    private static final String[] SEARCH_FIELDS = {
            "reversalNo", "counterpartyCode", "counterpartyName", "reason", "remark"
    };

    private final CashReversalRepository repository;
    private final CashReversalMapper mapper;
    private final PaymentRepository paymentRepository;
    private final ReceiptRepository receiptRepository;
    private final SupplierLedgerLockService ledgerLockService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public CashReversalService(CashReversalRepository repository,
                               SnowflakeIdGenerator idGenerator,
                               CashReversalMapper mapper,
                               PaymentRepository paymentRepository,
                               ReceiptRepository receiptRepository,
                               SupplierLedgerLockService ledgerLockService,
                               WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.mapper = mapper;
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
        this.ledgerLockService = ledgerLockService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<CashReversalResponse> page(PageQuery query, PageFilter filter) {
        Specification<CashReversal> spec = Specs.<CashReversal>keywordLike(filter.keyword(), SEARCH_FIELDS)
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("reversalDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    @Transactional(readOnly = true)
    public List<CashReversalResponse> search(String keyword, int maxSize) {
        return search(keyword, SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    @Transactional
    public CashReversalResponse update(Long id, CashReversalRequest request) {
        lockReversalRoot(id);
        return super.update(id, request);
    }

    @Override
    @Transactional
    public CashReversalResponse updateStatus(Long id, String status) {
        lockReversalRoot(id);
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        lockReversalRoot(id);
        super.delete(id);
    }

    @Override
    protected void validateCreate(CashReversalRequest request) {
        ensureReversalNoUnique(request.reversalNo());
    }

    @Override
    protected void validateUpdate(CashReversal entity, CashReversalRequest request) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核资金冲销单禁止修改");
        }
    }

    @Override
    protected CashReversalRequest normalizeCreateRequest(CashReversalRequest request, long entityId) {
        return new CashReversalRequest(
                resolveCreateBusinessNo(MODULE_KEY, request.reversalNo(), entityId),
                request.originalPaymentId(),
                request.originalReceiptId(),
                request.reversalDate(),
                request.amount(),
                request.reason(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected CashReversalRequest normalizeUpdateRequest(CashReversal entity, CashReversalRequest request) {
        return new CashReversalRequest(
                entity.getReversalNo(),
                request.originalPaymentId(),
                request.originalReceiptId(),
                request.reversalDate(),
                request.amount(),
                request.reason(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected CashReversal newEntity() {
        return new CashReversal();
    }

    @Override
    protected void assignId(CashReversal entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<CashReversal> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "资金冲销单不存在";
    }

    @Override
    protected Set<String> allowedStatusTransitions() {
        return Set.of(StatusConstants.DRAFT + "->" + StatusConstants.AUDITED);
    }

    @Override
    protected void apply(CashReversal entity, CashReversalRequest request) {
        assertExactlyOneSource(request.originalPaymentId(), request.originalReceiptId());
        String status = normalizeDraftStatus(request.status());
        SourceSnapshot source = loadSource(
                request.originalPaymentId(),
                request.originalReceiptId(),
                false
        );

        entity.setReversalNo(request.reversalNo());
        entity.setOriginalPaymentId(request.originalPaymentId());
        entity.setOriginalReceiptId(request.originalReceiptId());
        applySourceSnapshot(entity, source);
        entity.setReversalDate(requireNonNull(request.reversalDate(), "冲销日期不能为空"));
        entity.setAmount(normalizeAmount(request.amount()));
        entity.setReason(trimRequired(request.reason(), "冲销原因不能为空"));
        entity.setStatus(status);
        entity.setOperatorName(trimRequired(request.operatorName(), "经办人不能为空"));
        entity.setRemark(trimToNull(request.remark()));
    }

    @Override
    protected void beforeStatusUpdate(CashReversal entity, String currentStatus, String nextStatus) {
        if (StatusConstants.AUDITED.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核资金冲销单禁止反审核");
        }
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                MODULE_KEY,
                currentStatus,
                nextStatus,
                StatusConstants.AUDITED
        );
        SourceSnapshot source = loadSource(
                entity.getOriginalPaymentId(),
                entity.getOriginalReceiptId(),
                true
        );
        applySourceSnapshot(entity, source);
        ledgerLockService.lock(source.settlementCompanyId(), source.supplierId());
        BigDecimal auditedAmount = entity.getOriginalPaymentId() != null
                ? repository.sumAuditedAmountByOriginalPaymentIdExcludingId(
                        entity.getOriginalPaymentId(), entity.getId())
                : repository.sumAuditedAmountByOriginalReceiptIdExcludingId(
                        entity.getOriginalReceiptId(), entity.getId());
        BigDecimal accumulated = defaultZero(auditedAmount).add(entity.getAmount());
        if (accumulated.compareTo(source.amount()) > 0) {
            String sourceType = entity.getOriginalPaymentId() != null ? "付款" : "收款";
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "累计冲销金额不能超过原" + sourceType + "金额 " + source.amount()
            );
        }
    }

    @Override
    protected CashReversal saveEntity(CashReversal entity) {
        return repository.save(entity);
    }

    @Override
    protected CashReversalResponse toResponse(CashReversal entity) {
        return mapper.toResponse(entity);
    }

    private SourceSnapshot loadSource(Long paymentId, Long receiptId, boolean forUpdate) {
        assertExactlyOneSource(paymentId, receiptId);
        if (paymentId != null) {
            Payment payment = (forUpdate
                    ? paymentRepository.findByIdAndDeletedFlagFalseForUpdate(paymentId)
                    : paymentRepository.findByIdAndDeletedFlagFalse(paymentId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "原付款单不存在"));
            DataScopeContext.assertCanAccess(payment);
            requireAuditedSupplierSource(
                    payment.getStatus(),
                    payment.getCounterpartyType(),
                    payment.getCounterpartyId(),
                    payment.getSettlementCompanyId(),
                    "付款单"
            );
            return new SourceSnapshot(
                    payment.getCounterpartyId(),
                    payment.getCounterpartyCode(),
                    payment.getCounterpartyName(),
                    payment.getSettlementCompanyId(),
                    payment.getSettlementCompanyName(),
                    payment.getAmount()
            );
        }
        Receipt receipt = (forUpdate
                ? receiptRepository.findByIdAndDeletedFlagFalseForUpdate(receiptId)
                : receiptRepository.findByIdAndDeletedFlagFalse(receiptId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "原收款单不存在"));
        DataScopeContext.assertCanAccess(receipt);
        requireAuditedSupplierSource(
                receipt.getStatus(),
                receipt.getCounterpartyType(),
                receipt.getCounterpartyId(),
                receipt.getSettlementCompanyId(),
                "收款单"
        );
        return new SourceSnapshot(
                receipt.getCounterpartyId(),
                receipt.getCounterpartyCode(),
                receipt.getCounterpartyName(),
                receipt.getSettlementCompanyId(),
                receipt.getSettlementCompanyName(),
                receipt.getAmount()
        );
    }

    private void requireAuditedSupplierSource(String status,
                                              String counterpartyType,
                                              Long supplierId,
                                              Long settlementCompanyId,
                                              String sourceType) {
        if (!StatusConstants.AUDITED.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只能冲销已审核" + sourceType);
        }
        if (!SUPPLIER.equals(counterpartyType) || supplierId == null || settlementCompanyId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, sourceType + "不是有效的供应商资金单");
        }
    }

    private void applySourceSnapshot(CashReversal entity, SourceSnapshot source) {
        entity.setCounterpartyType(SUPPLIER);
        entity.setCounterpartyId(source.supplierId());
        entity.setCounterpartyCode(source.supplierCode());
        entity.setCounterpartyName(trimRequired(source.supplierName(), "原资金单缺少供应商名称"));
        entity.setSettlementCompanyId(source.settlementCompanyId());
        entity.setSettlementCompanyName(trimRequired(
                source.settlementCompanyName(),
                "原资金单缺少结算主体名称"
        ));
    }

    private void assertExactlyOneSource(Long paymentId, Long receiptId) {
        if ((paymentId == null) == (receiptId == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "原付款单和原收款单必须且只能选择一个");
        }
    }

    private String normalizeDraftStatus(String status) {
        String normalized = trimRequired(status, "状态不能为空");
        if (!StatusConstants.DRAFT.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资金冲销单只能以草稿状态创建或编辑");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "冲销金额必须大于0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    private String trimRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void ensureReversalNoUnique(String reversalNo) {
        if (repository.existsByReversalNoAndDeletedFlagFalse(reversalNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "资金冲销单号已存在");
        }
    }

    private void lockReversalRoot(Long id) {
        repository.findByIdAndDeletedFlagFalseForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage()));
    }

    private record SourceSnapshot(
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName,
            BigDecimal amount
    ) {
    }
}
