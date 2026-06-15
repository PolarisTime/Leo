package com.leo.erp.finance.payment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.common.service.SettlementAllocationRule;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.payment.web.dto.PaymentAllocationRequest;
import com.leo.erp.finance.payment.web.dto.PaymentRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class PaymentAllocationService {

    static final String SUPPLIER_PAYMENT_TYPE = "供应商";
    static final String FREIGHT_PAYMENT_TYPE = "物流商";
    static final String PAYMENT_STATUS_SETTLED = StatusConstants.PAID;

    private final PaymentStatementAllocationValidator statementAllocationValidator;

    public PaymentAllocationService(PaymentStatementAllocationValidator statementAllocationValidator) {
        this.statementAllocationValidator = statementAllocationValidator;
    }

    AllocationApplyResult applyAllocations(
            Payment entity,
            PaymentRequest request,
            String nextStatus,
            LongSupplier nextIdSupplier
    ) {
        List<PaymentAllocationRequest> allocationRequests = normalizeAllocationRequests(request);
        if (!supportsSettlement(request.businessType())) {
            if (PAYMENT_STATUS_SETTLED.equals(nextStatus)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已付款状态必须关联供应商或物流商对账单核销");
            }
            if (!allocationRequests.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前业务类型不支持对账单核销");
            }
            entity.getItems().clear();
            return new AllocationApplyResult(null, BigDecimal.ZERO, true);
        }

        String resolvedCounterpartyCode = null;
        BigDecimal totalAllocatedAmount = BigDecimal.ZERO;
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        List<PaymentAllocation> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                allocationRequests,
                PaymentAllocation::getId,
                PaymentAllocationRequest::id,
                PaymentAllocation::new,
                nextIdSupplier,
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
            String nextCounterpartyCode = statementAllocationValidator.validate(
                    request,
                    nextStatus,
                    entity.getId(),
                    source.sourceStatementId(),
                    allocatedAmount,
                    requestAllocatedAmountMap,
                    i + 1
            );
            resolvedCounterpartyCode = mergeCounterpartyCode(resolvedCounterpartyCode, nextCounterpartyCode);
            totalAllocatedAmount = totalAllocatedAmount.add(allocatedAmount);
        }

        if (totalAllocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(entity.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "核销金额合计不能超过付款金额");
        }
        assertSettlementAllocationsComplete(nextStatus, allocationRequests.isEmpty(), totalAllocatedAmount, entity.getAmount());
        entity.getItems().sort(java.util.Comparator.comparing(PaymentAllocation::getLineNo));
        return new AllocationApplyResult(resolvedCounterpartyCode, totalAllocatedAmount, allocationRequests.isEmpty());
    }

    void validateExistingAllocationsForSettlement(Payment entity, String nextStatus) {
        if (!PAYMENT_STATUS_SETTLED.equals(nextStatus)) {
            return;
        }
        assertBusinessTypeSupportsSettlement(entity.getBusinessType());
        PaymentRequest request = toStatusOnlyRequest(entity);
        assertSettlementAllocationsComplete(
                nextStatus,
                entity.getItems().isEmpty(),
                totalAllocatedAmount(entity.getItems()),
                entity.getAmount()
        );
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        for (int i = 0; i < entity.getItems().size(); i++) {
            PaymentAllocation item = entity.getItems().get(i);
            BigDecimal allocatedAmount = TradeItemCalculator.safeBigDecimal(item.getAllocatedAmount());
            statementAllocationValidator.validate(
                    request,
                    nextStatus,
                    entity.getId(),
                    item.getSourceStatementId(),
                    allocatedAmount,
                    requestAllocatedAmountMap,
                    i + 1
            );
        }
    }

    BigDecimal totalAllocatedAmount(List<PaymentAllocation> items) {
        return items.stream()
                .map(PaymentAllocation::getAllocatedAmount)
                .map(TradeItemCalculator::safeBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<PaymentAllocationRequest> normalizeAllocationRequests(PaymentRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items();
        }
        if (request.sourceStatementId() == null) {
            return List.of();
        }
        return List.of(new PaymentAllocationRequest(null, request.sourceStatementId(), request.amount()));
    }

    String mergeCounterpartyCode(String currentCode, String nextCode) {
        String normalizedCurrentCode = BusinessDocumentValidator.trimToNull(currentCode);
        String normalizedNextCode = BusinessDocumentValidator.trimToNull(nextCode);
        if (normalizedCurrentCode == null) {
            return normalizedNextCode;
        }
        if (normalizedNextCode == null || normalizedCurrentCode.equals(normalizedNextCode)) {
            return normalizedCurrentCode;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能核销不同往来单位编码的对账单");
    }

    private boolean supportsSettlement(String businessType) {
        return SUPPLIER_PAYMENT_TYPE.equals(businessType) || FREIGHT_PAYMENT_TYPE.equals(businessType);
    }

    private void assertBusinessTypeSupportsSettlement(String businessType) {
        if (supportsSettlement(businessType)) {
            return;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已付款状态必须关联供应商或物流商对账单核销");
    }

    private void assertSettlementAllocationsComplete(String nextStatus,
                                                     boolean allocationEmpty,
                                                     BigDecimal totalAllocatedAmount,
                                                     BigDecimal paymentAmount) {
        SettlementAllocationRule.requireCompleteForSettledStatus(
                nextStatus,
                PAYMENT_STATUS_SETTLED,
                allocationEmpty,
                totalAllocatedAmount,
                paymentAmount,
                "已付款状态必须填写核销明细",
                "付款金额必须等于核销金额合计"
        );
    }

    private PaymentRequest toStatusOnlyRequest(Payment entity) {
        return new PaymentRequest(
                entity.getPaymentNo(),
                entity.getBusinessType(),
                entity.getCounterpartyCode(),
                entity.getCounterpartyName(),
                entity.getSourceStatementId(),
                entity.getPaymentDate(),
                entity.getPayType(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getOperatorName(),
                entity.getRemark(),
                entity.getItems().stream()
                        .map(item -> new PaymentAllocationRequest(
                                item.getId(),
                                item.getSourceStatementId(),
                                item.getAllocatedAmount()
                        ))
                        .toList()
        );
    }

    private BigDecimal normalizeAllocatedAmount(BigDecimal allocatedAmount, int lineNo) {
        return SettlementAllocationRule.requirePositiveAmount(allocatedAmount, lineNo);
    }

    record AllocationApplyResult(
            String counterpartyCode,
            BigDecimal totalAllocatedAmount,
            boolean allocationEmpty
    ) {
    }
}
