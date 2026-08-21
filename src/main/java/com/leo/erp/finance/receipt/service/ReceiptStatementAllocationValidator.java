package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.SettlementAllocationRule;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.statement.api.CustomerStatementApi;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class ReceiptStatementAllocationValidator {

    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final CustomerStatementApi customerStatementApi;

    public ReceiptStatementAllocationValidator(ReceiptAllocationRepository receiptAllocationRepository,
                                               CustomerStatementApi customerStatementApi) {
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.customerStatementApi = customerStatementApi;
    }

    CustomerStatementApi.Snapshot validate(ReceiptRequest request,
                                           String normalizedStatus,
                                           Long currentReceiptId,
                                           Long sourceStatementId,
                                           BigDecimal allocatedAmount,
                                           Map<Long, BigDecimal> requestAllocatedAmountMap,
                                           int lineNo) {
        CustomerStatementApi.Snapshot statement = requireAccessibleCustomerStatement(sourceStatementId);
        requireSameIdentity(
                request.customerId(),
                statement.customerId(),
                "第" + lineNo + "行对账单客户ID与收款单不一致",
                "第" + lineNo + "行客户对账单缺少客户ID"
        );
        requireSameIdentity(
                request.projectId(),
                statement.projectId(),
                "第" + lineNo + "行对账单项目ID与收款单不一致",
                "第" + lineNo + "行客户对账单缺少项目ID"
        );
        BusinessDocumentValidator.requireSameText(
                request.customerName(),
                statement.customerName(),
                "第" + lineNo + "行对账单客户与收款单客户不一致"
        );
        BusinessDocumentValidator.requireSameText(
                request.projectName(),
                statement.projectName(),
                "第" + lineNo + "行对账单项目与收款单项目不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.customerCode(),
                statement.customerCode(),
                "第" + lineNo + "行对账单客户编码与收款单客户编码不一致"
        );
        if (requestAllocatedAmountMap.containsKey(statement.id())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一收款单不能重复核销同一客户对账单");
        }
        if (ReceiptAllocationService.RECEIPT_STATUS_SETTLED.equals(normalizedStatus)) {
            BusinessDocumentValidator.requireStatusIn(
                    statement.status(),
                    StatusConstants.SETTLEABLE_CUSTOMER_STATEMENT_STATUS,
                    "第" + lineNo + "行客户对账单未确认，不能收款"
            );
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    receiptAllocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                            statement.id(),
                            ReceiptAllocationService.RECEIPT_STATUS_SETTLED,
                            currentReceiptId
                    )
            );
            SettlementAllocationRule.assertWithinAllocatedLimit(
                    settledAmount,
                    allocatedAmount,
                    statement.salesAmount(),
                    "第" + lineNo + "行关联客户对账单累计收款金额不能超过销售金额"
            );
        }
        requestAllocatedAmountMap.put(statement.id(), allocatedAmount);
        return statement;
    }

    private void requireSameIdentity(Long requestedId,
                                     Long sourceId,
                                     String conflictMessage,
                                     String missingMessage) {
        if (sourceId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, missingMessage);
        }
        if (requestedId != null && !requestedId.equals(sourceId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, conflictMessage);
        }
    }

    private CustomerStatementApi.Snapshot requireAccessibleCustomerStatement(Long statementId) {
        return customerStatementApi.requireActiveById(statementId);
    }
}
