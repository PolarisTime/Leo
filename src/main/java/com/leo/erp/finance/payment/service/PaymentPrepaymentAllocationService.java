package com.leo.erp.finance.payment.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.common.service.SettlementAllocationRule;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.domain.entity.PaymentPurposes;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.finance.payment.repository.PaymentRepository;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationResponse;
import com.leo.erp.finance.payment.web.dto.PaymentPrepaymentAllocationUpdateRequest;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.service.SupplierStatementQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PaymentPrepaymentAllocationService {

    private static final String SUPPLIER_STATEMENT_MODULE_KEY = "supplier-statement";

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final SupplierStatementQueryService supplierStatementQueryService;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;
    private final SnowflakeIdGenerator idGenerator;
    private final PaymentSettlementSyncService settlementSyncService;
    private final PaymentAllocationResponseAssembler responseAssembler;

    public PaymentPrepaymentAllocationService(PaymentRepository paymentRepository,
                                              PaymentAllocationRepository paymentAllocationRepository,
                                              SupplierStatementQueryService supplierStatementQueryService,
                                              SourceAllocationLockService sourceAllocationLockService,
                                              ResourceRecordAccessGuard resourceRecordAccessGuard,
                                              SnowflakeIdGenerator idGenerator,
                                              PaymentSettlementSyncService settlementSyncService,
                                              PaymentAllocationResponseAssembler responseAssembler) {
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.supplierStatementQueryService = supplierStatementQueryService;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
        this.idGenerator = idGenerator;
        this.settlementSyncService = settlementSyncService;
        this.responseAssembler = responseAssembler;
    }

    @Transactional
    public List<PaymentAllocationResponse> replaceAllocations(
            Long paymentId,
            PaymentPrepaymentAllocationUpdateRequest request
    ) {
        Payment payment = paymentRepository.findByIdAndDeletedFlagFalseForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "付款单不存在"));
        DataScopeContext.assertCanAccess(payment);
        assertEligiblePayment(payment);
        List<NormalizedAllocation> allocations = normalizeAllocations(request);
        assertPaymentCapacity(payment, allocations);

        sourceAllocationLockService.lockStatementSources(
                List.of(),
                affectedStatementIds(payment, allocations),
                List.of()
        );
        validateStatements(payment, allocations);

        settlementSyncService.captureOriginalAllocationState(payment);
        replaceItems(payment, allocations);
        Payment saved = paymentRepository.saveAndFlush(payment);
        settlementSyncService.syncLinkedStatements(saved);
        return responseAssembler.toResponses(saved);
    }

    private void assertEligiblePayment(Payment payment) {
        if (!PaymentPurposes.isPurchasePrepayment(payment.getPaymentPurpose())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅采购预付款支持后续分配供应商对账单");
        }
        if (!StatusConstants.PAID.equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅已付款的采购预付款支持分配供应商对账单");
        }
        if (!PaymentAllocationService.SUPPLIER_PAYMENT_TYPE.equals(payment.getBusinessType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购预付款业务类型必须为供应商");
        }
        if (BusinessDocumentValidator.trimToNull(payment.getSupplierCode()) == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购预付款供应商编码不能为空");
        }
        if (payment.getSettlementCompanyId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购预付款结算主体不能为空");
        }
    }

    private List<NormalizedAllocation> normalizeAllocations(PaymentPrepaymentAllocationUpdateRequest request) {
        if (request == null || request.items() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "采购预付款核销明细不能为空");
        }
        List<NormalizedAllocation> allocations = new ArrayList<>(request.items().size());
        Set<Long> statementIds = new HashSet<>();
        for (int index = 0; index < request.items().size(); index++) {
            PaymentAllocationRequest item = request.items().get(index);
            int lineNo = index + 1;
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行核销明细不能为空");
            }
            if (item.sourceStatementId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行核销对账单不能为空");
            }
            if (!statementIds.add(item.sourceStatementId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能重复核销同一供应商对账单");
            }
            allocations.add(new NormalizedAllocation(
                    item.id(),
                    item.sourceStatementId(),
                    SettlementAllocationRule.requirePositiveAmount(item.allocatedAmount(), lineNo),
                    lineNo
            ));
        }
        return List.copyOf(allocations);
    }

    private void assertPaymentCapacity(Payment payment, List<NormalizedAllocation> allocations) {
        BigDecimal allocatedAmount = allocations.stream()
                .map(NormalizedAllocation::allocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(payment.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "核销金额合计不能超过采购预付款金额");
        }
    }

    private List<Long> affectedStatementIds(Payment payment, List<NormalizedAllocation> allocations) {
        TreeSet<Long> statementIds = new TreeSet<>();
        if (payment.getItems() != null) {
            payment.getItems().stream()
                    .map(PaymentAllocation::getSourceStatementId)
                    .filter(Objects::nonNull)
                    .forEach(statementIds::add);
        }
        allocations.stream()
                .map(NormalizedAllocation::sourceStatementId)
                .forEach(statementIds::add);
        return List.copyOf(statementIds);
    }

    private void validateStatements(Payment payment, List<NormalizedAllocation> allocations) {
        String supplierCode = BusinessDocumentValidator.trimToNull(payment.getSupplierCode());
        for (NormalizedAllocation allocation : allocations) {
            SupplierStatement statement = supplierStatementQueryService.requireActiveById(
                    allocation.sourceStatementId()
            );
            resourceRecordAccessGuard.assertCurrentUserCanAccess(
                    SUPPLIER_STATEMENT_MODULE_KEY,
                    ResourcePermissionCatalog.READ,
                    statement
            );
            validateStatementIdentity(payment, supplierCode, statement, allocation.lineNo());
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    paymentAllocationRepository
                            .sumAllocatedAmountBySourceStatementIdAndBusinessTypeAndStatusExcludingPaymentId(
                                    statement.getId(),
                                    PaymentAllocationService.SUPPLIER_PAYMENT_TYPE,
                                    StatusConstants.PAID,
                                    payment.getId()
                            )
            );
            if (settledAmount.add(allocation.allocatedAmount())
                    .compareTo(TradeItemCalculator.safeBigDecimal(statement.getPurchaseAmount())) > 0) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + allocation.lineNo() + "行关联供应商对账单累计付款金额不能超过采购金额"
                );
            }
        }
    }

    private void validateStatementIdentity(Payment payment,
                                           String supplierCode,
                                           SupplierStatement statement,
                                           int lineNo) {
        BusinessDocumentValidator.requireStatusIn(
                statement.getStatus(),
                StatusConstants.SETTLEABLE_SUPPLIER_STATEMENT_STATUS,
                "第" + lineNo + "行供应商对账单未确认，不能核销采购预付款"
        );
        if (!Objects.equals(supplierCode, BusinessDocumentValidator.trimToNull(statement.getSupplierCode()))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行供应商对账单供应商编码与采购预付款不一致"
            );
        }
        if (!Objects.equals(payment.getSettlementCompanyId(), statement.getSettlementCompanyId())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行供应商对账单结算主体与采购预付款不一致"
            );
        }
    }

    private void replaceItems(Payment payment, List<NormalizedAllocation> allocations) {
        List<PaymentAllocation> currentItems = payment.getItems() == null
                ? new ArrayList<>()
                : new ArrayList<>(payment.getItems());
        List<PaymentAllocation> nextItems = ManagedEntityItemSupport.syncById(
                currentItems,
                allocations,
                PaymentAllocation::getId,
                NormalizedAllocation::id,
                PaymentAllocation::new,
                idGenerator::nextId,
                PaymentAllocation::setId
        );
        for (int index = 0; index < nextItems.size(); index++) {
            PaymentAllocation item = nextItems.get(index);
            NormalizedAllocation source = allocations.get(index);
            item.setPayment(payment);
            item.setLineNo(index + 1);
            item.setSourceStatementId(source.sourceStatementId());
            item.setAllocatedAmount(source.allocatedAmount());
        }
        if (payment.getItems() == null) {
            payment.setItems(new ArrayList<>());
        } else {
            payment.getItems().clear();
        }
        payment.getItems().addAll(nextItems);
    }

    private record NormalizedAllocation(
            Long id,
            Long sourceStatementId,
            BigDecimal allocatedAmount,
            int lineNo
    ) {
    }
}
