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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class PaymentAllocationService {

    static final String SUPPLIER_PAYMENT_TYPE = "供应商";
    static final String FREIGHT_PAYMENT_TYPE = "物流商";
    static final String PAYMENT_STATUS_SETTLED = StatusConstants.AUDITED;

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
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核状态必须关联物流商对账单核销");
            }
            if (!allocationRequests.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前业务类型不支持对账单核销");
            }
            entity.getItems().clear();
            return new AllocationApplyResult(null, null, null, BigDecimal.ZERO, true);
        }

        String resolvedCounterpartyCode = null;
        Long resolvedCounterpartyId = null;
        SettlementCompanySnapshot settlementCompany = null;
        BigDecimal totalAllocatedAmount = BigDecimal.ZERO;
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        List<PaymentAllocation> items = ManagedEntityItemSupport.syncById(
                new ArrayList<>(entity.getItems()),
                allocationRequests,
                PaymentAllocation::getId,
                PaymentAllocationRequest::id,
                PaymentAllocation::new,
                nextIdSupplier,
                PaymentAllocation::setId
        );

        for (int i = 0; i < allocationRequests.size(); i++) {
            PaymentAllocationRequest source = allocationRequests.get(i);
            int lineNo = i + 1;
            Long sourceStatementId = resolveSourceStatementId(source, request.businessType(), lineNo);
            BigDecimal allocatedAmount = normalizeAllocatedAmount(source.allocatedAmount(), i + 1);
            PaymentAllocation item = items.get(i);
            item.setPayment(entity);
            item.setLineNo(lineNo);
            applyTypedSource(item, request.businessType(), sourceStatementId);
            item.setAllocatedAmount(allocatedAmount);
            PaymentStatementAllocationValidator.ValidatedStatement validatedStatement =
                    statementAllocationValidator.validate(
                            request,
                            nextStatus,
                            entity.getId(),
                            sourceStatementId,
                            allocatedAmount,
                            requestAllocatedAmountMap,
                            lineNo
                    );
            resolvedCounterpartyCode = mergeCounterpartyCode(
                    resolvedCounterpartyCode,
                    validatedStatement.counterpartyCode()
            );
            resolvedCounterpartyId = mergeCounterpartyId(
                    resolvedCounterpartyId,
                    validatedStatement.counterpartyId()
            );
            settlementCompany = mergeSettlementCompany(settlementCompany, validatedStatement);
            totalAllocatedAmount = totalAllocatedAmount.add(allocatedAmount);
        }

        if (totalAllocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(entity.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "核销金额合计不能超过付款金额");
        }
        assertSettlementAllocationsComplete(nextStatus, allocationRequests.isEmpty(), totalAllocatedAmount, entity.getAmount());
        entity.getItems().clear();
        entity.getItems().addAll(items);
        entity.getItems().sort(java.util.Comparator.comparing(PaymentAllocation::getLineNo));
        return new AllocationApplyResult(
                request.businessType(),
                resolvedCounterpartyId,
                resolvedCounterpartyCode,
                settlementCompany == null ? null : settlementCompany.id(),
                settlementCompany == null ? null : settlementCompany.name(),
                totalAllocatedAmount,
                allocationRequests.isEmpty()
        );
    }

    void validateExistingAllocationsForSettlement(Payment entity, String nextStatus) {
        if (!StatusConstants.AUDITED.equals(nextStatus)) {
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
        String resolvedCounterpartyCode = null;
        Long resolvedCounterpartyId = null;
        SettlementCompanySnapshot settlementCompany = null;
        for (int i = 0; i < entity.getItems().size(); i++) {
            PaymentAllocation item = entity.getItems().get(i);
            Long sourceStatementId = requireExistingSourceStatementId(item, entity.getBusinessType(), i + 1);
            BigDecimal allocatedAmount = TradeItemCalculator.safeBigDecimal(item.getAllocatedAmount());
            PaymentStatementAllocationValidator.ValidatedStatement validatedStatement =
                    statementAllocationValidator.validate(
                            request,
                            nextStatus,
                            entity.getId(),
                            sourceStatementId,
                            allocatedAmount,
                            requestAllocatedAmountMap,
                            i + 1
                    );
            resolvedCounterpartyCode = mergeCounterpartyCode(
                    resolvedCounterpartyCode,
                    validatedStatement.counterpartyCode()
            );
            resolvedCounterpartyId = mergeCounterpartyId(
                    resolvedCounterpartyId,
                    validatedStatement.counterpartyId()
            );
            settlementCompany = mergeSettlementCompany(settlementCompany, validatedStatement);
        }
        entity.setCounterpartyCode(mergeCounterpartyCode(entity.getCounterpartyCode(), resolvedCounterpartyCode));
        entity.setCounterpartyType(entity.getBusinessType());
        entity.setCounterpartyId(mergeCounterpartyId(entity.getCounterpartyId(), resolvedCounterpartyId));
        entity.setSettlementCompanyId(settlementCompany == null ? null : settlementCompany.id());
        entity.setSettlementCompanyName(settlementCompany == null ? null : settlementCompany.name());
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
        return List.of(typedAllocationRequest(
                request.businessType(),
                request.sourceStatementId(),
                request.amount()
        ));
    }

    private PaymentAllocationRequest typedAllocationRequest(String businessType,
                                                            Long sourceStatementId,
                                                            BigDecimal allocatedAmount) {
        return new PaymentAllocationRequest(
                null,
                sourceStatementId,
                FREIGHT_PAYMENT_TYPE.equals(businessType) ? sourceStatementId : null,
                allocatedAmount
        );
    }

    private Long resolveSourceStatementId(PaymentAllocationRequest request,
                                          String businessType,
                                          int lineNo) {
        Long typedSourceId;
        if (!FREIGHT_PAYMENT_TYPE.equals(businessType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅物流商对账单支持付款核销");
        }
        typedSourceId = request.sourceFreightStatementId();
        Long legacySourceId = request.sourceStatementId();
        if (typedSourceId != null && legacySourceId != null && !typedSourceId.equals(legacySourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行类型化对账单ID与兼容来源ID不一致");
        }
        Long resolved = typedSourceId == null ? legacySourceId : typedSourceId;
        if (resolved == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "第" + lineNo + "行核销对账单不能为空");
        }
        return resolved;
    }

    private Long requireExistingSourceStatementId(PaymentAllocation item,
                                                  String businessType,
                                                  int lineNo) {
        Long typedSourceId = item.getSourceFreightStatementId();
        Long sourceStatementId = typedSourceId == null ? item.getSourceStatementId() : typedSourceId;
        if (sourceStatementId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行付款核销明细缺少对账单ID");
        }
        if (item.getSourceStatementId() != null && !sourceStatementId.equals(item.getSourceStatementId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行付款核销来源ID冲突");
        }
        applyTypedSource(item, businessType, sourceStatementId);
        return sourceStatementId;
    }

    private void applyTypedSource(PaymentAllocation item, String businessType, Long sourceStatementId) {
        item.setSourceStatementId(sourceStatementId);
        item.setSourceSupplierStatementId(null);
        item.setSourceFreightStatementId(sourceStatementId);
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

    private Long mergeCounterpartyId(Long currentId, Long nextId) {
        if (currentId == null) {
            return nextId;
        }
        if (nextId == null || currentId.equals(nextId)) {
            return currentId;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能核销不同往来方ID的对账单");
    }

    private SettlementCompanySnapshot mergeSettlementCompany(
            SettlementCompanySnapshot current,
            PaymentStatementAllocationValidator.ValidatedStatement next
    ) {
        SettlementCompanySnapshot nextSnapshot = new SettlementCompanySnapshot(
                next.settlementCompanyId(),
                next.settlementCompanyName()
        );
        if (current == null) {
            return nextSnapshot;
        }
        if (current.id().equals(nextSnapshot.id())) {
            return current;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一付款单不能核销不同结算主体的对账单");
    }

    private boolean supportsSettlement(String businessType) {
        return FREIGHT_PAYMENT_TYPE.equals(businessType);
    }

    private void assertBusinessTypeSupportsSettlement(String businessType) {
        if (supportsSettlement(businessType)) {
            return;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核状态必须关联物流商对账单核销");
    }

    private void assertSettlementAllocationsComplete(String nextStatus,
                                                     boolean allocationEmpty,
                                                     BigDecimal totalAllocatedAmount,
                                                     BigDecimal paymentAmount) {
        SettlementAllocationRule.requireCompleteForSettledStatus(
                nextStatus,
                StatusConstants.AUDITED,
                allocationEmpty,
                totalAllocatedAmount,
                paymentAmount,
                "已审核状态必须填写核销明细",
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
            String counterpartyType,
            Long counterpartyId,
            String counterpartyCode,
            Long settlementCompanyId,
            String settlementCompanyName,
            BigDecimal totalAllocatedAmount,
            boolean allocationEmpty
    ) {
        AllocationApplyResult(String counterpartyCode,
                              Long settlementCompanyId,
                              String settlementCompanyName,
                              BigDecimal totalAllocatedAmount,
                              boolean allocationEmpty) {
            this(null, null, counterpartyCode, settlementCompanyId, settlementCompanyName,
                    totalAllocatedAmount, allocationEmpty);
        }
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }
}
