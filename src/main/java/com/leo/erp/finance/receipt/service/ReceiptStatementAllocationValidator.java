package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.finance.receipt.repository.ReceiptAllocationRepository;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.permission.ResourceRecordAccessGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.service.CustomerStatementQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class ReceiptStatementAllocationValidator {

    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final CustomerStatementQueryService customerStatementQueryService;
    private final ResourceRecordAccessGuard resourceRecordAccessGuard;

    public ReceiptStatementAllocationValidator(ReceiptAllocationRepository receiptAllocationRepository,
                                               CustomerStatementQueryService customerStatementQueryService,
                                               ResourceRecordAccessGuard resourceRecordAccessGuard) {
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.customerStatementQueryService = customerStatementQueryService;
        this.resourceRecordAccessGuard = resourceRecordAccessGuard;
    }

    CustomerStatement validate(ReceiptRequest request,
                               String normalizedStatus,
                               Long currentReceiptId,
                               Long sourceStatementId,
                               BigDecimal allocatedAmount,
                               Map<Long, BigDecimal> requestAllocatedAmountMap,
                               int lineNo) {
        CustomerStatement statement = requireAccessibleCustomerStatement(sourceStatementId);
        requireSameIdentity(
                request.customerId(),
                statement.getCustomerId(),
                "第" + lineNo + "行对账单客户ID与收款单不一致",
                "第" + lineNo + "行客户对账单缺少客户ID"
        );
        requireSameIdentity(
                request.projectId(),
                statement.getProjectId(),
                "第" + lineNo + "行对账单项目ID与收款单不一致",
                "第" + lineNo + "行客户对账单缺少项目ID"
        );
        BusinessDocumentValidator.requireSameText(
                request.customerName(),
                statement.getCustomerName(),
                "第" + lineNo + "行对账单客户与收款单客户不一致"
        );
        BusinessDocumentValidator.requireSameText(
                request.projectName(),
                statement.getProjectName(),
                "第" + lineNo + "行对账单项目与收款单项目不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.customerCode(),
                statement.getCustomerCode(),
                "第" + lineNo + "行对账单客户编码与收款单客户编码不一致"
        );
        if (requestAllocatedAmountMap.containsKey(statement.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同一收款单不能重复核销同一客户对账单");
        }
        if (ReceiptAllocationService.RECEIPT_STATUS_SETTLED.equals(normalizedStatus)) {
            BusinessDocumentValidator.requireStatusIn(
                    statement.getStatus(),
                    StatusConstants.SETTLEABLE_CUSTOMER_STATEMENT_STATUS,
                    "第" + lineNo + "行客户对账单未确认，不能收款"
            );
            BigDecimal settledAmount = TradeItemCalculator.safeBigDecimal(
                    receiptAllocationRepository.sumAllocatedAmountBySourceStatementIdAndReceiptStatusExcludingReceiptId(
                            statement.getId(),
                            ReceiptAllocationService.RECEIPT_STATUS_SETTLED,
                            currentReceiptId
                    )
            );
            BigDecimal nextSettledAmount = settledAmount.add(allocatedAmount);
            if (nextSettledAmount.compareTo(statement.getSalesAmount()) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行关联客户对账单累计收款金额不能超过销售金额");
            }
        }
        requestAllocatedAmountMap.put(statement.getId(), allocatedAmount);
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

    private CustomerStatement requireAccessibleCustomerStatement(Long statementId) {
        CustomerStatement statement = customerStatementQueryService.requireActiveById(statementId);
        resourceRecordAccessGuard.assertCurrentUserCanAccess(
                "customer-statement",
                ResourcePermissionCatalog.READ,
                statement
        );
        return statement;
    }
}
