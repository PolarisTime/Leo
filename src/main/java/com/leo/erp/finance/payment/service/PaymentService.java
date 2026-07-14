package com.leo.erp.finance.payment.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.finance.purchaseflow.service.SupplierLedgerLockService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Service
public class PaymentService extends AbstractCrudService<Payment, PaymentRequest, PaymentResponse> {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentApplyService applyService;
    private final PaymentAllocationService paymentAllocationService;
    private final PaymentAllocationResponseAssembler allocationResponseAssembler;
    private final PaymentSettlementSyncService settlementSyncService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final PaymentPurchasePrepaymentService purchasePrepaymentService;
    private SupplierLedgerLockService supplierLedgerLockService;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          PaymentMapper paymentMapper,
                          PaymentApplyService applyService,
                          PaymentAllocationService paymentAllocationService,
                          PaymentAllocationResponseAssembler allocationResponseAssembler,
                          PaymentSettlementSyncService settlementSyncService,
                          SourceAllocationLockService sourceAllocationLockService,
                          PaymentPurchasePrepaymentService purchasePrepaymentService) {
        super(snowflakeIdGenerator);
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.applyService = applyService;
        this.paymentAllocationService = paymentAllocationService;
        this.allocationResponseAssembler = allocationResponseAssembler;
        this.settlementSyncService = settlementSyncService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.purchasePrepaymentService = purchasePrepaymentService;
    }

    @Autowired(required = false)
    void setSupplierLedgerLockService(SupplierLedgerLockService supplierLedgerLockService) {
        this.supplierLedgerLockService = supplierLedgerLockService;
    }

    public Page<PaymentResponse> page(PageQuery query, PageFilter filter) {
        Specification<Payment> spec = Specs.<Payment>keywordLike(filter.keyword(), "paymentNo", "businessType", "counterpartyName")
                .and(Specs.equalIfPresent("businessType", filter.businessType()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("paymentDate", filter.startDate(), filter.endDate()));
        return page(query, spec, paymentRepository);
    }

    private static final String[] PAYMENT_SEARCH_FIELDS = {
            "paymentNo",
            "businessType",
            "counterpartyName"
    };

    public List<PaymentResponse> search(String keyword, int maxSize) {
        return search(keyword, PAYMENT_SEARCH_FIELDS, maxSize, null, paymentRepository);
    }

    @Override
    @Transactional
    public PaymentResponse updateStatus(Long id, String status) {
        lockPaymentRoot(id);
        return super.updateStatus(id, status);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        lockPaymentRoot(id);
        super.delete(id);
    }

    @Override
    protected void validateCreate(PaymentRequest request) {
        ensurePaymentNoUnique(request.paymentNo());
    }

    @Override
    protected void validateUpdate(Payment entity, PaymentRequest request) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核付款单禁止修改，请使用资金冲销单纠错");
        }
        assertLegacySupplierPaymentReadOnly(entity, "修改");
        if (!entity.getPaymentNo().equals(request.paymentNo())) {
            ensurePaymentNoUnique(request.paymentNo());
        }
    }

    @Override
    protected PaymentRequest normalizeCreateRequest(PaymentRequest request, long entityId) {
        return new PaymentRequest(
                resolveCreateBusinessNo("payment", request.paymentNo(), entityId),
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
                request.paymentDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
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
                request.paymentDate(),
                request.payType(),
                request.amount(),
                request.status(),
                request.operatorName(),
                request.remark(),
                request.items()
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
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return java.util.Set.of(StatusConstants.DRAFT + "->" + StatusConstants.AUDITED);
    }

    @Override
    protected void beforeStatusUpdate(Payment entity, String currentStatus, String nextStatus) {
        if (StatusConstants.AUDITED.equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核付款单禁止反审核，请使用资金冲销单纠错");
        }
        assertLegacySupplierPaymentReadOnly(entity, "审核");
        if (PaymentPurposes.isPurchasePrepayment(entity.getPaymentPurpose())) {
            purchasePrepaymentService.applySourceSnapshot(
                    entity,
                    entity.getSourcePurchaseOrderId(),
                    entity.getAmount(),
                    nextStatus
            );
            lockSupplierLedgerMutation(entity);
            return;
        }
        if (PaymentPurposes.isSupplierTotalPayment(entity.getPaymentPurpose())) {
            lockSupplierLedgerMutation(entity);
            return;
        }
        if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(entity.getCounterpartyType())) {
            lockSupplierLedgerMutation(entity);
        }
        lockAllocationStatements(entity, null);
        settlementSyncService.captureOriginalAllocationState(entity);
        paymentAllocationService.validateExistingAllocationsForSettlement(entity, nextStatus);
    }

    @Override
    protected void beforeDelete(Payment entity) {
        if (StatusConstants.AUDITED.equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核付款单禁止删除，请使用资金冲销单纠错");
        }
        assertLegacySupplierPaymentReadOnly(entity, "删除");
        if (PaymentPurposes.isPurchasePrepayment(entity.getPaymentPurpose())) {
            purchasePrepaymentService.validateNoStatementAllocations(entity);
            return;
        }
        lockAllocationStatements(entity, null);
    }

    @Override
    protected PaymentResponse toDetailResponse(Payment entity) {
        PaymentResponse response = paymentMapper.toResponse(entity);
        return new PaymentResponse(
                response.id(),
                response.paymentNo(),
                response.businessType(),
                response.counterpartyId(),
                response.paymentPurpose(),
                response.counterpartyCode(),
                response.counterpartyName(),
                response.sourceStatementId(),
                response.sourcePurchaseOrderId(),
                response.purchaseOrderNo(),
                response.supplierCode(),
                response.supplierName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.paymentDate(),
                response.payType(),
                response.amount(),
                response.status(),
                response.deletedFlag(),
                response.operatorName(),
                response.remark(),
                allocationResponseAssembler.toResponses(entity)
        );
    }

    @Override
    protected PaymentResponse toSavedResponse(Payment entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void apply(Payment entity, PaymentRequest request) {
        lockAllocationStatements(entity, request);
        applyService.apply(entity, request, this::nextId);
    }

    private void lockAllocationStatements(Payment entity, PaymentRequest request) {
        TreeSet<Long> supplierStatementIds = new TreeSet<>();
        TreeSet<Long> freightStatementIds = new TreeSet<>();
        if (entity != null
                && !PaymentPurposes.isPurchasePrepayment(entity.getPaymentPurpose())
                && !PaymentPurposes.isSupplierTotalPayment(entity.getPaymentPurpose())) {
            addStatementIds(
                    entity.getBusinessType(),
                    existingAllocationStatementIds(entity),
                    supplierStatementIds,
                    freightStatementIds
            );
        }
        if (request != null
                && !PaymentPurposes.isPurchasePrepayment(request.paymentPurpose())
                && !PaymentPurposes.isSupplierTotalPayment(request.paymentPurpose())) {
            addStatementIds(
                    request.businessType(),
                    requestedAllocationStatementIds(request),
                    supplierStatementIds,
                    freightStatementIds
            );
        }
        sourceAllocationLockService.lockStatementSources(
                List.of(),
                List.copyOf(supplierStatementIds),
                List.copyOf(freightStatementIds)
        );
    }

    private List<Long> existingAllocationStatementIds(Payment entity) {
        if (entity.getItems() != null && !entity.getItems().isEmpty()) {
            return entity.getItems().stream()
                    .map(item -> allocationStatementId(entity.getBusinessType(), item))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return entity.getSourceStatementId() == null
                ? List.of()
                : List.of(entity.getSourceStatementId());
    }

    private List<Long> requestedAllocationStatementIds(PaymentRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items().stream()
                    .map(item -> allocationStatementId(request.businessType(), item))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return request.sourceStatementId() == null
                ? List.of()
                : List.of(request.sourceStatementId());
    }

    private Long allocationStatementId(String businessType, PaymentAllocation item) {
        Long typedId = PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(businessType)
                ? item.getSourceSupplierStatementId()
                : item.getSourceFreightStatementId();
        return typedId == null ? item.getSourceStatementId() : typedId;
    }

    private Long allocationStatementId(String businessType, PaymentAllocationRequest item) {
        Long typedId = PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(businessType)
                ? item.sourceSupplierStatementId()
                : item.sourceFreightStatementId();
        return typedId == null ? item.sourceStatementId() : typedId;
    }

    private void addStatementIds(String businessType,
                                 List<Long> statementIds,
                                 TreeSet<Long> supplierStatementIds,
                                 TreeSet<Long> freightStatementIds) {
        if (PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(businessType)) {
            supplierStatementIds.addAll(statementIds);
        } else if (PaymentAllocationService.FREIGHT_PAYMENT_TYPE.equals(businessType)) {
            freightStatementIds.addAll(statementIds);
        }
    }

    @Override
    protected Payment saveEntity(Payment entity) {
        Payment saved = paymentRepository.save(entity);
        settlementSyncService.syncLinkedStatements(saved);
        return saved;
    }

    @Override
    protected PaymentResponse toResponse(Payment entity) {
        return paymentMapper.toResponse(entity);
    }

    private void ensurePaymentNoUnique(String paymentNo) {
        if (paymentRepository.existsByPaymentNoAndDeletedFlagFalse(paymentNo)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单号已存在");
        }
    }

    private void lockPaymentRoot(Long id) {
        paymentRepository.findByIdAndDeletedFlagFalseForUpdate(id);
    }

    private SupplierLedgerLockService requireSupplierLedgerLockService() {
        if (supplierLedgerLockService == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商账簿锁服务不可用");
        }
        return supplierLedgerLockService;
    }

    private void lockSupplierLedgerMutation(Payment entity) {
        if (entity.getCounterpartyId() == null || entity.getSettlementCompanyId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商付款缺少供应商或结算主体身份");
        }
        requireSupplierLedgerLockService().lock(
                entity.getSettlementCompanyId(),
                entity.getCounterpartyId()
        );
    }

    private void assertLegacySupplierPaymentReadOnly(Payment entity, String operation) {
        boolean purchasePrepayment = PaymentPurposes.isPurchasePrepayment(entity.getPaymentPurpose());
        boolean supplierStatementSettlement = PaymentPurposes.STATEMENT_SETTLEMENT.equals(
                PaymentPurposes.normalize(entity.getPaymentPurpose())
        ) && PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(entity.getBusinessType());
        if (purchasePrepayment || supplierStatementSettlement) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "旧采购预付款及供应商对账付款仅供历史查询，不允许" + operation
            );
        }
    }
}
