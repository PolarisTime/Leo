package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.common.service.SettlementAllocationRule;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class ReceiptAllocationService {

    static final String RECEIPT_STATUS_SETTLED = StatusConstants.AUDITED;

    private final ReceiptStatementAllocationValidator statementAllocationValidator;

    public ReceiptAllocationService(ReceiptStatementAllocationValidator statementAllocationValidator) {
        this.statementAllocationValidator = statementAllocationValidator;
    }

    AllocationApplyResult applyAllocations(
            Receipt entity,
            ReceiptRequest request,
            String nextStatus,
            LongSupplier nextIdSupplier
    ) {
        List<ReceiptAllocationRequest> allocationRequests = normalizeAllocationRequests(request);
        String resolvedCustomerCode = null;
        Long resolvedCustomerId = null;
        Long resolvedProjectId = null;
        SettlementCompanySnapshot settlementCompany = SettlementCompanySnapshot.EMPTY;
        BigDecimal totalAllocatedAmount = BigDecimal.ZERO;
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        List<ReceiptAllocation> items = ManagedEntityItemSupport.syncById(
                new ArrayList<>(entity.getItems()),
                allocationRequests,
                ReceiptAllocation::getId,
                ReceiptAllocationRequest::id,
                ReceiptAllocation::new,
                nextIdSupplier,
                ReceiptAllocation::setId
        );

        for (int i = 0; i < allocationRequests.size(); i++) {
            ReceiptAllocationRequest source = allocationRequests.get(i);
            BigDecimal allocatedAmount = normalizeAllocatedAmount(source.allocatedAmount(), i + 1);
            CustomerStatement statement = statementAllocationValidator.validate(
                    request,
                    nextStatus,
                    entity.getId(),
                    source.sourceStatementId(),
                    allocatedAmount,
                    requestAllocatedAmountMap,
                    i + 1
            );
            resolvedCustomerId = mergeIdentity(resolvedCustomerId, statement.getCustomerId(), "客户");
            resolvedProjectId = mergeIdentity(resolvedProjectId, statement.getProjectId(), "项目");
            resolvedCustomerCode = mergeCustomerCode(resolvedCustomerCode, statement.getCustomerCode());
            settlementCompany = mergeSettlementCompany(settlementCompany, statement, i + 1);

            ReceiptAllocation item = items.get(i);
            item.setReceipt(entity);
            item.setLineNo(i + 1);
            item.setSourceStatementId(statement.getId());
            item.setSourceCustomerStatementId(statement.getId());
            item.setAllocatedAmount(allocatedAmount);
            totalAllocatedAmount = totalAllocatedAmount.add(allocatedAmount);
        }

        if (totalAllocatedAmount.compareTo(TradeItemCalculator.safeBigDecimal(entity.getAmount())) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "核销金额合计不能超过收款金额");
        }
        assertSettlementAllocationsComplete(nextStatus, allocationRequests.isEmpty(), totalAllocatedAmount, entity.getAmount());
        entity.getItems().clear();
        entity.getItems().addAll(items);
        entity.getItems().sort(java.util.Comparator.comparing(ReceiptAllocation::getLineNo));
        return new AllocationApplyResult(
                resolvedCustomerId,
                resolvedProjectId,
                resolvedCustomerCode,
                settlementCompany.id(),
                settlementCompany.name(),
                totalAllocatedAmount,
                allocationRequests.isEmpty()
        );
    }

    void validateExistingAllocationsForSettlement(Receipt entity, String nextStatus) {
        if (!StatusConstants.AUDITED.equals(nextStatus)) {
            return;
        }
        ReceiptRequest request = toStatusOnlyRequest(entity);
        assertSettlementAllocationsComplete(
                nextStatus,
                entity.getItems().isEmpty(),
                totalAllocatedAmount(entity.getItems()),
                entity.getAmount()
        );
        Map<Long, BigDecimal> requestAllocatedAmountMap = new HashMap<>();
        String resolvedCustomerCode = null;
        Long resolvedCustomerId = null;
        Long resolvedProjectId = null;
        SettlementCompanySnapshot settlementCompany = SettlementCompanySnapshot.EMPTY;
        for (int i = 0; i < entity.getItems().size(); i++) {
            ReceiptAllocation item = entity.getItems().get(i);
            CustomerStatement statement = statementAllocationValidator.validate(
                    request,
                    nextStatus,
                    entity.getId(),
                    item.getSourceStatementId(),
                    TradeItemCalculator.safeBigDecimal(item.getAllocatedAmount()),
                    requestAllocatedAmountMap,
                    i + 1
            );
            item.setSourceCustomerStatementId(statement.getId());
            resolvedCustomerId = mergeIdentity(resolvedCustomerId, statement.getCustomerId(), "客户");
            resolvedProjectId = mergeIdentity(resolvedProjectId, statement.getProjectId(), "项目");
            resolvedCustomerCode = mergeCustomerCode(resolvedCustomerCode, statement.getCustomerCode());
            settlementCompany = mergeSettlementCompany(settlementCompany, statement, i + 1);
        }
        entity.setCustomerCode(mergeCustomerCode(entity.getCustomerCode(), resolvedCustomerCode));
        entity.setCustomerId(mergeIdentity(entity.getCustomerId(), resolvedCustomerId, "客户"));
        entity.setProjectId(mergeIdentity(entity.getProjectId(), resolvedProjectId, "项目"));
        entity.setSettlementCompanyId(settlementCompany.id());
        entity.setSettlementCompanyName(settlementCompany.name());
    }

    String mergeCustomerCode(String currentCode, String nextCode) {
        String normalizedCurrentCode = BusinessDocumentValidator.trimToNull(currentCode);
        String normalizedNextCode = BusinessDocumentValidator.trimToNull(nextCode);
        if (normalizedCurrentCode == null) {
            return normalizedNextCode;
        }
        if (normalizedNextCode == null || normalizedCurrentCode.equals(normalizedNextCode)) {
            return normalizedCurrentCode;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一收款单不能核销不同客户编码的对账单");
    }

    private Long mergeIdentity(Long currentId, Long nextId, String label) {
        if (currentId == null) {
            return nextId;
        }
        if (nextId == null || currentId.equals(nextId)) {
            return currentId;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一收款单不能核销不同" + label + "ID的对账单");
    }

    BigDecimal totalAllocatedAmount(List<ReceiptAllocation> items) {
        return items.stream()
                .map(ReceiptAllocation::getAllocatedAmount)
                .map(TradeItemCalculator::safeBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<ReceiptAllocationRequest> normalizeAllocationRequests(ReceiptRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            return request.items();
        }
        if (request.sourceStatementId() == null) {
            return List.of();
        }
        return List.of(new ReceiptAllocationRequest(null, request.sourceStatementId(), request.amount()));
    }

    private void assertSettlementAllocationsComplete(String nextStatus,
                                                     boolean allocationEmpty,
                                                     BigDecimal totalAllocatedAmount,
                                                     BigDecimal receiptAmount) {
        SettlementAllocationRule.requireCompleteForSettledStatus(
                nextStatus,
                StatusConstants.AUDITED,
                allocationEmpty,
                totalAllocatedAmount,
                receiptAmount,
                "已审核状态必须填写核销明细",
                "收款金额必须等于核销金额合计"
        );
    }

    private ReceiptRequest toStatusOnlyRequest(Receipt entity) {
        return new ReceiptRequest(
                entity.getReceiptNo(),
                entity.getCustomerId(),
                entity.getCustomerCode(),
                entity.getCustomerName(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getSettlementCompanyId(),
                entity.getSettlementCompanyName(),
                entity.getSourceStatementId(),
                entity.getReceiptDate(),
                entity.getPayType(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getOperatorName(),
                entity.getRemark(),
                entity.getItems().stream()
                        .map(item -> new ReceiptAllocationRequest(
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

    private SettlementCompanySnapshot mergeSettlementCompany(SettlementCompanySnapshot current,
                                                             CustomerStatement statement,
                                                             int lineNo) {
        SettlementCompanySnapshot next = new SettlementCompanySnapshot(
                statement.getSettlementCompanyId(),
                BusinessDocumentValidator.trimToNull(statement.getSettlementCompanyName())
        );
        if (next.isEmpty()) {
            return current;
        }
        if (current.isEmpty()) {
            return next;
        }
        if (!current.equals(next)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行客户对账单结算主体与收款单不一致");
        }
        return current;
    }

    record AllocationApplyResult(
            Long customerId,
            Long projectId,
            String customerCode,
            Long settlementCompanyId,
            String settlementCompanyName,
            BigDecimal totalAllocatedAmount,
            boolean allocationEmpty
    ) {
    }

    private record SettlementCompanySnapshot(Long id, String name) {

        private static final SettlementCompanySnapshot EMPTY = new SettlementCompanySnapshot(null, null);

        boolean isEmpty() {
            return id == null && name == null;
        }
    }
}
