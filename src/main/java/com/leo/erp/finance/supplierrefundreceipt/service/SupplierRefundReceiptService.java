package com.leo.erp.finance.supplierrefundreceipt.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.supplierrefundreceipt.domain.entity.SupplierRefundReceipt;
import com.leo.erp.finance.supplierrefundreceipt.mapper.SupplierRefundReceiptMapper;
import com.leo.erp.finance.supplierrefundreceipt.repository.SupplierRefundReceiptRepository;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptRequest;
import com.leo.erp.finance.supplierrefundreceipt.web.dto.SupplierRefundReceiptResponse;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.refund.domain.entity.PurchaseRefund;
import com.leo.erp.purchase.refund.repository.PurchaseRefundRepository;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Service
public class SupplierRefundReceiptService extends AbstractCrudService<
        SupplierRefundReceipt,
        SupplierRefundReceiptRequest,
        SupplierRefundReceiptResponse> {

    private static final String MODULE_KEY = "supplier-refund-receipt";
    private static final String[] SEARCH_FIELDS = {
            "refundReceiptNo",
            "supplierCode",
            "supplierName",
            "settlementCompanyName"
    };
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            StatusConstants.DRAFT,
            StatusConstants.RECEIVED
    );

    private final SupplierRefundReceiptRepository repository;
    private final PurchaseRefundRepository purchaseRefundRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final SupplierRefundReceiptMapper mapper;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SupplierRefundReceiptService(SupplierRefundReceiptRepository repository,
                                        PurchaseRefundRepository purchaseRefundRepository,
                                        PurchaseOrderItemRepository purchaseOrderItemRepository,
                                        SnowflakeIdGenerator idGenerator,
                                        SourceAllocationLockService sourceAllocationLockService,
                                        SupplierRefundReceiptMapper mapper,
                                        ResourceRecordAccessGuard resourceRecordAccessGuard,
                                        WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseRefundRepository = purchaseRefundRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.mapper = mapper;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<SupplierRefundReceiptResponse> page(PageQuery query, PageFilter filter) {
        Specification<SupplierRefundReceipt> specification = Specs
                .<SupplierRefundReceipt>keywordLike(filter.keyword(), SEARCH_FIELDS)
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalValueIfPresent("settlementCompanyId", filter.settlementCompanyId()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("receiptDate", filter.startDate(), filter.endDate()));
        return page(query, specification, repository);
    }

    @Transactional(readOnly = true)
    public List<SupplierRefundReceiptResponse> search(String keyword, int maxSize) {
        return search(keyword, SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected void validateCreate(SupplierRefundReceiptRequest request) {
        ensureRefundReceiptNoUnique(request.refundReceiptNo());
    }

    @Override
    protected void validateUpdate(SupplierRefundReceipt entity, SupplierRefundReceiptRequest request) {
        if (!entity.getRefundReceiptNo().equals(request.refundReceiptNo())) {
            ensureRefundReceiptNoUnique(request.refundReceiptNo());
        }
    }

    @Override
    protected SupplierRefundReceiptRequest normalizeCreateRequest(
            SupplierRefundReceiptRequest request,
            long entityId
    ) {
        return new SupplierRefundReceiptRequest(
                resolveCreateBusinessNo(MODULE_KEY, request.refundReceiptNo(), entityId),
                request.purchaseRefundId(),
                request.receiptDate(),
                request.receiptMethod(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected SupplierRefundReceiptRequest normalizeUpdateRequest(
            SupplierRefundReceipt entity,
            SupplierRefundReceiptRequest request
    ) {
        return new SupplierRefundReceiptRequest(
                entity.getRefundReceiptNo(),
                request.purchaseRefundId(),
                request.receiptDate(),
                request.receiptMethod(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark()
        );
    }

    @Override
    protected SupplierRefundReceipt newEntity() {
        return new SupplierRefundReceipt();
    }

    @Override
    protected void assignId(SupplierRefundReceipt entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SupplierRefundReceipt> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<SupplierRefundReceipt> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "供应商退款到账单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected Set<String> allowedStatusTransitions() {
        return Set.of(
                StatusConstants.DRAFT + "->" + StatusConstants.RECEIVED,
                StatusConstants.RECEIVED + "->" + StatusConstants.DRAFT
        );
    }

    @Override
    protected void apply(SupplierRefundReceipt entity, SupplierRefundReceiptRequest request) {
        PurchaseRefund purchaseRefund = lockAndRequireAuditedRefund(entity, request.purchaseRefundId());
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                entity.getStatus() == null ? StatusConstants.DRAFT : entity.getStatus(),
                "供应商退款到账状态",
                ALLOWED_STATUSES
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                MODULE_KEY,
                entity.getStatus(),
                nextStatus,
                StatusConstants.RECEIVED
        );
        BigDecimal amount = normalizeAmount(request.amount());
        if (StatusConstants.RECEIVED.equals(nextStatus)) {
            assertReceiptAmountWithinRefund(purchaseRefund, amount, entity.getId());
        }

        entity.setRefundReceiptNo(request.refundReceiptNo());
        entity.setPurchaseRefundId(purchaseRefund.getId());
        entity.setSupplierCode(trimRequired(purchaseRefund.getSupplierCode(), "采购退款单供应商编码"));
        entity.setSupplierName(trimRequired(purchaseRefund.getSupplierName(), "采购退款单供应商名称"));
        applySettlementCompanySnapshot(entity, purchaseRefund);
        entity.setReceiptDate(request.receiptDate());
        entity.setReceiptMethod(trimRequired(request.receiptMethod(), "到账方式"));
        entity.setAmount(amount);
        entity.setStatus(nextStatus);
        entity.setOperatorName(trimRequired(request.operatorName(), "经办人"));
        entity.setRemark(trimToNull(request.remark()));
    }

    @Override
    protected void beforeStatusUpdate(
            SupplierRefundReceipt entity,
            String currentStatus,
            String nextStatus
    ) {
        PurchaseRefund purchaseRefund = lockAndRequireAuditedRefund(entity, entity.getPurchaseRefundId());
        if (StatusConstants.RECEIVED.equals(nextStatus)) {
            assertReceiptAmountWithinRefund(purchaseRefund, entity.getAmount(), entity.getId());
        }
    }

    @Override
    protected void beforeDelete(SupplierRefundReceipt entity) {
        lockAndRequireAuditedRefund(entity, entity.getPurchaseRefundId());
    }

    @Override
    protected SupplierRefundReceipt saveEntity(SupplierRefundReceipt entity) {
        return repository.save(entity);
    }

    @Override
    protected SupplierRefundReceiptResponse toResponse(SupplierRefundReceipt entity) {
        return mapper.toResponse(entity);
    }

    private PurchaseRefund lockAndRequireAuditedRefund(
            SupplierRefundReceipt receipt,
            Long targetPurchaseRefundId
    ) {
        if (targetPurchaseRefundId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购退款单不能为空");
        }
        TreeSet<Long> affectedRefundIds = new TreeSet<>();
        if (receipt != null && receipt.getPurchaseRefundId() != null) {
            affectedRefundIds.add(receipt.getPurchaseRefundId());
        }
        affectedRefundIds.add(targetPurchaseRefundId);

        Map<Long, PurchaseRefund> refundsBeforeLock = new TreeMap<>();
        TreeSet<Long> sourcePurchaseOrderItemIds = new TreeSet<>();
        for (Long refundId : affectedRefundIds) {
            PurchaseRefund purchaseRefund = requirePurchaseRefund(refundId);
            resourceRecordAccessGuard.assertCurrentUserCanAccess(
                    "purchase-refund",
                    "read",
                    purchaseRefund
            );
            assertAudited(purchaseRefund);
            Long sourcePurchaseOrderId = purchaseRefund.getSourcePurchaseOrderId();
            if (sourcePurchaseOrderId == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购退款单缺少来源采购订单");
            }
            List<Long> sourceItemIds = purchaseOrderItemRepository
                    .findActiveIdsByPurchaseOrderId(sourcePurchaseOrderId);
            if (sourceItemIds.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源采购订单明细不存在");
            }
            sourcePurchaseOrderItemIds.addAll(sourceItemIds);
            refundsBeforeLock.put(refundId, purchaseRefund);
        }

        sourceAllocationLockService.lockTradeItemSources(
                List.copyOf(sourcePurchaseOrderItemIds),
                List.of(),
                List.of()
        );

        Map<Long, PurchaseRefund> lockedRefunds = new TreeMap<>();
        for (Long refundId : affectedRefundIds) {
            PurchaseRefund lockedRefund = requirePurchaseRefund(refundId);
            assertAudited(lockedRefund);
            PurchaseRefund beforeLock = refundsBeforeLock.get(refundId);
            if (!beforeLock.getSourcePurchaseOrderId().equals(lockedRefund.getSourcePurchaseOrderId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购退款单来源已变更，请刷新后重试");
            }
            lockedRefunds.put(refundId, lockedRefund);
        }
        return lockedRefunds.get(targetPurchaseRefundId);
    }

    private PurchaseRefund requirePurchaseRefund(Long purchaseRefundId) {
        return purchaseRefundRepository.findByIdAndDeletedFlagFalse(purchaseRefundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "采购退款单不存在"));
    }

    private void assertAudited(PurchaseRefund purchaseRefund) {
        if (!StatusConstants.AUDITED.equals(purchaseRefund.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购退款单未审核，不能登记供应商退款到账");
        }
    }

    private void assertReceiptAmountWithinRefund(
            PurchaseRefund purchaseRefund,
            BigDecimal currentAmount,
            Long excludedReceiptId
    ) {
        BigDecimal refundAmount = purchaseRefund.getTotalAmount();
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购退款金额不合法");
        }
        BigDecimal otherReceivedAmount = repository
                .sumReceivedAmountByPurchaseRefundIdExcludingReceiptId(
                        purchaseRefund.getId(),
                        excludedReceiptId
                );
        BigDecimal normalizedReceivedAmount = otherReceivedAmount == null
                ? BigDecimal.ZERO
                : otherReceivedAmount;
        if (normalizedReceivedAmount.add(currentAmount).compareTo(refundAmount) > 0) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "供应商退款累计到账金额不能超过采购退款金额"
            );
        }
    }

    private void applySettlementCompanySnapshot(
            SupplierRefundReceipt receipt,
            PurchaseRefund purchaseRefund
    ) {
        Long settlementCompanyId = purchaseRefund.getSettlementCompanyId();
        String settlementCompanyName = trimToNull(purchaseRefund.getSettlementCompanyName());
        if ((settlementCompanyId == null) != (settlementCompanyName == null)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购退款单结算主体信息不完整");
        }
        receipt.setSettlementCompanyId(settlementCompanyId);
        receipt.setSettlementCompanyName(settlementCompanyName);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "到账金额必须大于0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimRequired(String value, String fieldLabel) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, fieldLabel + "不能为空");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void ensureRefundReceiptNoUnique(String refundReceiptNo) {
        if (repository.existsByRefundReceiptNoAndDeletedFlagFalse(refundReceiptNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商退款到账单号已存在");
        }
    }
}
