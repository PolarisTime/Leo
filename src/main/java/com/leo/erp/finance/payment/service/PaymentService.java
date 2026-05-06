package com.leo.erp.finance.payment.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PaymentService extends AbstractCrudService<Payment, PaymentRequest, PaymentResponse> {

    private static final String SUPPLIER_PAYMENT_TYPE = "供应商";
    private static final String FREIGHT_PAYMENT_TYPE = "物流商";
    private static final String PAYMENT_STATUS_SETTLED = StatusConstants.PAID;

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PaymentMapper paymentMapper;
    private final SupplierStatementQueryService supplierStatementQueryService;
    private final FreightStatementQueryService freightStatementQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAllocationRepository paymentAllocationRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          PaymentMapper paymentMapper,
                          SupplierStatementQueryService supplierStatementQueryService,
                          FreightStatementQueryService freightStatementQueryService,
                          ApplicationEventPublisher eventPublisher,
                          ResourceRecordAccessGuard resourceRecordAccessGuard,
                          WorkflowTransitionGuard workflowTransitionGuard) {
        super(snowflakeIdGenerator);
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.paymentMapper = paymentMapper;
        this.supplierStatementQueryService = supplierStatementQueryService;
        this.freightStatementQueryService = freightStatementQueryService;
        this.eventPublisher = eventPublisher;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    public Page<PaymentResponse> page(PageQuery query,
                                      String keyword,
                                      String businessType,
                                      String status,
                                      LocalDate startDate,
                                      LocalDate endDate) {
        Specification<Payment> spec = Specs.<Payment>notDeleted()
                .and(Specs.keywordLike(keyword, "paymentNo", "businessType", "counterpartyName"))
                .and(Specs.equalIfPresent("businessType", businessType))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("paymentDate", startDate, endDate));
        return page(query, spec, paymentRepository);
    }

    @Override
    protected void validateCreate(PaymentRequest request) {
        ensurePaymentNoUnique(request.paymentNo());
    }

    @Override
    protected void validateUpdate(Payment entity, PaymentRequest request) {
        if (!entity.getPaymentNo().equals(request.paymentNo())) {
            ensurePaymentNoUnique(request.paymentNo());
        }
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
    protected String notFoundMessage() {
        return "付款单不存在";
    }

    @Override
    protected PaymentResponse toDetailResponse(Payment entity) {
        PaymentResponse response = paymentMapper.toResponse(entity);
        return new PaymentResponse(
                response.id(),
                response.paymentNo(),
                response.businessType(),
                response.counterpartyName(),
                response.sourceStatementId(),
                response.paymentDate(),
                response.payType(),
                response.amount(),
                response.status(),
                response.operatorName(),
                response.remark(),
                entity.getItems().stream().map(item -> toAllocationResponse(entity.getBusinessType(), item)).toList()
        );
    }

    @Override
    protected PaymentResponse toSavedResponse(Payment entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void apply(Payment entity, PaymentRequest request) {
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "payments",
                entity.getStatus(),
                request.status(),
                PAYMENT_STATUS_SETTLED
        );
        captureOriginalAllocationState(entity);
        entity.setPaymentNo(request.paymentNo());
        entity.setBusinessType(request.businessType());
        entity.setCounterpartyName(request.counterpartyName());
        entity.setPaymentDate(request.paymentDate());
        entity.setPayType(request.payType());
        entity.setAmount(TradeItemCalculator.scaleAmount(request.amount()));
        entity.setStatus(request.status());
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
        applyAllocations(entity, request);
        entity.setSourceStatementId(resolveLegacySourceStatementId(entity.getItems()));
    }

    @Override
    protected Payment saveEntity(Payment entity) {
        Payment saved = paymentRepository.save(entity);
        syncLinkedStatements(saved);
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

    private void applyAllocations(Payment entity, PaymentRequest request) {
        List<PaymentAllocationRequest> allocationRequests = normalizeAllocationRequests(request);
        if (!SUPPLIER_PAYMENT_TYPE.equals(request.businessType()) && !FREIGHT_PAYMENT_TYPE.equals(request.businessType())) {
            if (!allocationRequests.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前业务类型不支持对账单核销");
            }
            entity.getItems().clear();
            return;
        }

        BigDecimal totalAllocatedAmount = BigDecimal.ZERO;
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        List<PaymentAllocation> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                allocationRequests,
                PaymentAllocation::getId,
                PaymentAllocationRequest::id,
                PaymentAllocation::new,
                this::nextId,
                PaymentAllocation::setId
        );

        for (int i = 0; i < allocationRequests.size(); i++) {
            PaymentAllocationRequest source = allocationRequests.get(i);
            BigDecimal allocatedAmount = normalizeAllocatedAmount(source.allocatedAmount(), i + 1);
            PaymentAllocation item = items.get(i);
            item.setPayment(entity);
            item.setLineNo(i + 1);
            item.setSourceStatementId(source.sourceStatementId());
            item.setAllocatedAmount(allocatedAmount);
            if (SUPPLIER_PAYMENT_TYPE.equals(request.businessType())) {
                SupplierStatement statement = requireAccessibleSupplierStatement(source.sourceStatementId());
                validateLinkedSupplierStatement(request, entity.getId(), statement, allocatedAmount, requestAllocatedAmountMap, i + 1);
            } else {
                FreightStatement statement = requireAccessibleFreightStatement(source.sourceStatementId());
                validateLinkedFreightStatement(request, entity.getId(), statement, allocatedAmount, requestAllocatedAmountMap, i + 1);
            }
            totalAllocatedAmount = totalAllocatedAmount.add(allocatedAmount);
        }

        if (totalAllocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(entity.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "核销金额合计不能超过付款金额");
        }
        entity.getItems().sort(java.util.Comparator.comparing(PaymentAllocation::getLineNo));
    }

    private List<PaymentAllocationRequest> normalizeAllocationRequests(PaymentRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items();
        }
        if (request.sourceStatementId() == null) {
            return List.of();
        }
        return List.of(new PaymentAllocationRequest(null, request.sourceStatementId(), request.amount()));
    }

    private SupplierStatement requireAccessibleSupplierStatement(Long statementId) {
        SupplierStatement statement = supplierStatementQueryService.requireActiveById(statementId);
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "supplier-statements",
                ResourcePermissionCatalog.READ,
                statement
        );
        return statement;
    }

    private FreightStatement requireAccessibleFreightStatement(Long statementId) {
        FreightStatement statement = freightStatementQueryService.requireActiveById(statementId);
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "freight-statements",
                ResourcePermissionCatalog.READ,
                statement
        );
        return statement;
    }

    private void validateLinkedSupplierStatement(PaymentRequest request,
                                                 Long currentPaymentId,
                                                 SupplierStatement statement,
                                                 BigDecimal allocatedAmount,
                                                 Map<Long, BigDecimal> requestAllocatedAmountMap,
                                                 int lineNo) {
        if (!statement.getSupplierName().equals(request.counterpartyName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行对账单供应商与付款单往来单位不一致");
        }
        if (requestAllocatedAmountMap.containsKey(statement.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能重复核销同一供应商对账单");
        }
        if (PAYMENT_STATUS_SETTLED.equals(request.status())) {
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    paymentAllocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                            statement.getId(),
                            SUPPLIER_PAYMENT_TYPE,
                            PAYMENT_STATUS_SETTLED,
                            currentPaymentId
                    )
            );
            BigDecimal nextSettledAmount = settledAmount.add(allocatedAmount);
            if (nextSettledAmount.compareTo(statement.getPurchaseAmount()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行关联供应商对账单累计付款金额不能超过采购金额");
            }
        }
        requestAllocatedAmountMap.put(statement.getId(), allocatedAmount);
    }

    private void validateLinkedFreightStatement(PaymentRequest request,
                                                Long currentPaymentId,
                                                FreightStatement statement,
                                                BigDecimal allocatedAmount,
                                                Map<Long, BigDecimal> requestAllocatedAmountMap,
                                                int lineNo) {
        if (!statement.getCarrierName().equals(request.counterpartyName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行对账单物流商与付款单往来单位不一致");
        }
        if (requestAllocatedAmountMap.containsKey(statement.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能重复核销同一物流对账单");
        }
        if (PAYMENT_STATUS_SETTLED.equals(request.status())) {
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    paymentAllocationRepository.sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                            statement.getId(),
                            FREIGHT_PAYMENT_TYPE,
                            PAYMENT_STATUS_SETTLED,
                            currentPaymentId
                    )
            );
            BigDecimal nextSettledAmount = settledAmount.add(allocatedAmount);
            if (nextSettledAmount.compareTo(statement.getTotalFreight()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行关联物流对账单累计付款金额不能超过总运费");
            }
        }
        requestAllocatedAmountMap.put(statement.getId(), allocatedAmount);
    }

    private BigDecimal normalizeAllocatedAmount(BigDecimal allocatedAmount, int lineNo) {
        BigDecimal normalized = TradeItemCalculator.safeBigDecimal(allocatedAmount);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行核销金额必须大于0");
        }
        return TradeItemCalculator.scaleAmount(normalized);
    }

    private void captureOriginalAllocationState(Payment entity) {
        entity.setOriginalBusinessType(entity.getBusinessType());
        entity.setOriginalAllocationStatementIds(collectStatementIds(entity.getItems()));
    }

    private Set<Long> collectStatementIds(List<PaymentAllocation> items) {
        Set<Long> statementIds = new LinkedHashSet<>();
        for (PaymentAllocation item : items) {
            if (item.getSourceStatementId() != null) {
                statementIds.add(item.getSourceStatementId());
            }
        }
        return statementIds;
    }

    private Long resolveLegacySourceStatementId(List<PaymentAllocation> items) {
        return items.size() == 1 ? items.get(0).getSourceStatementId() : null;
    }

    private void syncLinkedStatements(Payment entity) {
        Set<StatementLink> links = new LinkedHashSet<>();
        if (entity.getOriginalBusinessType() != null) {
            entity.getOriginalAllocationStatementIds()
                    .forEach(statementId -> links.add(new StatementLink(entity.getOriginalBusinessType(), statementId)));
        }
        entity.getItems()
                .forEach(item -> links.add(new StatementLink(entity.getBusinessType(), item.getSourceStatementId())));
        for (StatementLink link : links) {
            if (link.statementId() == null || link.businessType() == null) {
                continue;
            }
            eventPublisher.publishEvent(new PaymentSettledEvent(link.statementId(), link.businessType()));
        }
    }

    private PaymentAllocationResponse toAllocationResponse(String businessType, PaymentAllocation item) {
        if (SUPPLIER_PAYMENT_TYPE.equals(businessType)) {
            SupplierStatement statement = item.getSourceStatementId() == null
                    ? null
                    : supplierStatementQueryService.findActiveById(item.getSourceStatementId()).orElse(null);
            return new PaymentAllocationResponse(
                    item.getId(),
                    item.getLineNo(),
                    item.getSourceStatementId(),
                    statement == null ? null : statement.getStatementNo(),
                    statement == null ? BigDecimal.ZERO : statement.getClosingAmount(),
                    item.getAllocatedAmount()
            );
        }
        FreightStatement statement = item.getSourceStatementId() == null
                ? null
                : freightStatementQueryService.findActiveById(item.getSourceStatementId()).orElse(null);
        return new PaymentAllocationResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceStatementId(),
                statement == null ? null : statement.getStatementNo(),
                statement == null ? BigDecimal.ZERO : statement.getUnpaidAmount(),
                item.getAllocatedAmount()
        );
    }

    private record StatementLink(String businessType, Long statementId) {
    }
}
