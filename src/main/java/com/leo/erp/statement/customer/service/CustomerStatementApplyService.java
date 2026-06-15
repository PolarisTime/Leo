package com.leo.erp.statement.customer.service;

import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.service.StatementBalanceRule;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class CustomerStatementApplyService {

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final CustomerStatementSourceService sourceService;

    public CustomerStatementApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                                         CustomerStatementSourceService sourceService) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.sourceService = sourceService;
    }

    void apply(CustomerStatement entity, CustomerStatementRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.PENDING_CONFIRM,
                "客户对账单状态",
                StatusConstants.ALLOWED_STATEMENT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "customer-statement",
                entity.getStatus(),
                nextStatus,
                StatusConstants.CONFIRMED
        );
        entity.setStatementNo(request.statementNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setProjectId(request.projectId());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal salesAmount = sourceService.applyItems(entity, request, nextIdSupplier);
        StatementBalanceRule.Balance balance = StatementBalanceRule.resolve(
                salesAmount,
                request.receiptAmount(),
                "客户对账单收款金额",
                "客户对账单销售金额不能低于已收款金额"
        );
        entity.setSalesAmount(balance.sourceAmount());
        entity.setReceiptAmount(balance.settledAmount());
        entity.setClosingAmount(balance.closingAmount());
    }
}
