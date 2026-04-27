package com.leo.erp.finance.payment.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.mapper.PaymentMapper;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.service.FreightStatementQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class PaymentService extends AbstractCrudService<Payment, PaymentRequest, PaymentResponse> {

    private static final String SUPPLIER_PAYMENT_TYPE = "供应商";
    private static final String FREIGHT_PAYMENT_TYPE = "物流商";

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final SupplierStatementQueryService supplierStatementQueryService;
    private final FreightStatementQueryService freightStatementQueryService;
    private final StatementSettlementSyncService statementSettlementSyncService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public PaymentService(PaymentRepository paymentRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          PaymentMapper paymentMapper,
                          SupplierStatementQueryService supplierStatementQueryService,
                          FreightStatementQueryService freightStatementQueryService,
                          StatementSettlementSyncService statementSettlementSyncService,
                          ResourceRecordAccessGuard resourceRecordAccessGuard,
                          WorkflowTransitionGuard workflowTransitionGuard) {
        super(snowflakeIdGenerator);
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.supplierStatementQueryService = supplierStatementQueryService;
        this.freightStatementQueryService = freightStatementQueryService;
        this.statementSettlementSyncService = statementSettlementSyncService;
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
    protected void apply(Payment entity, PaymentRequest request) {
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "payments",
                entity.getStatus(),
                request.status(),
                StatementSettlementSyncService.PAYMENT_STATUS_SETTLED
        );
        entity.setPaymentNo(request.paymentNo());
        entity.setBusinessType(request.businessType());
        entity.setCounterpartyName(request.counterpartyName());
        entity.setSourceStatementId(resolveLinkedStatementId(request, entity.getId()));
        entity.setPaymentDate(request.paymentDate());
        entity.setPayType(request.payType());
        entity.setAmount(request.amount());
        entity.setStatus(request.status());
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());
    }

    @Override
    protected Payment saveEntity(Payment entity) {
        Long originalSourceStatementId = entity.getOriginalSourceStatementId();
        String originalBusinessType = entity.getOriginalBusinessType();
        Payment saved = paymentRepository.save(entity);
        syncLinkedStatements(
                originalBusinessType,
                originalSourceStatementId,
                saved.getBusinessType(),
                saved.getSourceStatementId()
        );
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

    private Long resolveLinkedStatementId(PaymentRequest request, Long currentPaymentId) {
        if (SUPPLIER_PAYMENT_TYPE.equals(request.businessType())) {
            return resolveLinkedSupplierStatement(request, currentPaymentId).getId();
        }
        if (FREIGHT_PAYMENT_TYPE.equals(request.businessType())) {
            return resolveLinkedFreightStatement(request, currentPaymentId).getId();
        }
        if (request.sourceStatementId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前业务类型不支持关联对账单");
        }
        return null;
    }

    private SupplierStatement resolveLinkedSupplierStatement(PaymentRequest request, Long currentPaymentId) {
        if (request.sourceStatementId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商付款必须关联供应商对账单");
        }
        SupplierStatement statement = supplierStatementQueryService.requireActiveById(request.sourceStatementId());
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "supplier-statements",
                ResourcePermissionCatalog.READ,
                statement
        );
        if (!statement.getSupplierName().equals(request.counterpartyName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单往来单位与供应商对账单供应商不一致");
        }
        if (StatementSettlementSyncService.PAYMENT_STATUS_SETTLED.equals(request.status())) {
            BigDecimal settledAmount = safeAmount(paymentRepository.sumAmountBySourceStatementIdAndStatusExcludingId(
                    statement.getId(),
                    StatementSettlementSyncService.PAYMENT_STATUS_SETTLED,
                    currentPaymentId
            ));
            BigDecimal nextSettledAmount = settledAmount.add(request.amount());
            if (nextSettledAmount.compareTo(statement.getPurchaseAmount()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "关联供应商对账单累计付款金额不能超过采购金额");
            }
        }
        return statement;
    }

    private FreightStatement resolveLinkedFreightStatement(PaymentRequest request, Long currentPaymentId) {
        if (request.sourceStatementId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物流付款必须关联物流对账单");
        }
        FreightStatement statement = freightStatementQueryService.requireActiveById(request.sourceStatementId());
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "freight-statements",
                ResourcePermissionCatalog.READ,
                statement
        );
        if (!statement.getCarrierName().equals(request.counterpartyName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "付款单往来单位与物流对账单物流商不一致");
        }
        if (StatementSettlementSyncService.PAYMENT_STATUS_SETTLED.equals(request.status())) {
            BigDecimal settledAmount = safeAmount(paymentRepository.sumAmountBySourceStatementIdAndStatusExcludingId(
                    statement.getId(),
                    StatementSettlementSyncService.PAYMENT_STATUS_SETTLED,
                    currentPaymentId
            ));
            BigDecimal nextSettledAmount = settledAmount.add(request.amount());
            if (nextSettledAmount.compareTo(statement.getTotalFreight()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "关联物流对账单累计付款金额不能超过总运费");
            }
        }
        return statement;
    }

    private void syncLinkedStatements(String originalBusinessType,
                                      Long originalSourceStatementId,
                                      String currentBusinessType,
                                      Long currentSourceStatementId) {
        syncLinkedStatement(originalBusinessType, originalSourceStatementId);
        if (shouldSkipDuplicateSync(originalBusinessType, originalSourceStatementId, currentBusinessType, currentSourceStatementId)) {
            return;
        }
        syncLinkedStatement(currentBusinessType, currentSourceStatementId);
    }

    private void syncLinkedStatement(String businessType, Long sourceStatementId) {
        if (sourceStatementId == null) {
            return;
        }
        if (SUPPLIER_PAYMENT_TYPE.equals(businessType)) {
            supplierStatementQueryService.findActiveById(sourceStatementId)
                    .ifPresent(statementSettlementSyncService::syncSupplierStatement);
            return;
        }
        if (FREIGHT_PAYMENT_TYPE.equals(businessType)) {
            freightStatementQueryService.findActiveById(sourceStatementId)
                    .ifPresent(statementSettlementSyncService::syncFreightStatement);
        }
    }

    private boolean shouldSkipDuplicateSync(String originalBusinessType,
                                            Long originalSourceStatementId,
                                            String currentBusinessType,
                                            Long currentSourceStatementId) {
        return originalSourceStatementId != null
                && originalSourceStatementId.equals(currentSourceStatementId)
                && SUPPLIER_PAYMENT_TYPE.equals(originalBusinessType) == SUPPLIER_PAYMENT_TYPE.equals(currentBusinessType)
                && FREIGHT_PAYMENT_TYPE.equals(originalBusinessType) == FREIGHT_PAYMENT_TYPE.equals(currentBusinessType);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
