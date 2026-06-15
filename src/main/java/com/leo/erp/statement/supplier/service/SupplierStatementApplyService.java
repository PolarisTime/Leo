package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.service.StatementBalanceRule;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class SupplierStatementApplyService {

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SupplierStatementSourceService sourceService;

    public SupplierStatementApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                                         SupplierStatementSourceService sourceService) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.sourceService = sourceService;
    }

    void apply(SupplierStatement entity, SupplierStatementRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.PENDING_CONFIRM,
                "供应商对账单状态",
                StatusConstants.ALLOWED_STATEMENT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "supplier-statement",
                entity.getStatus(),
                nextStatus,
                StatusConstants.CONFIRMED
        );
        entity.setStatementNo(request.statementNo());
        entity.setSupplierName(request.supplierName());
        BigDecimal purchaseAmount = sourceService.applyItems(entity, request, nextIdSupplier);
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        StatementBalanceRule.Balance balance = StatementBalanceRule.resolve(
                purchaseAmount,
                request.paymentAmount(),
                "供应商对账单付款金额",
                "供应商对账单采购金额不能低于已付款金额"
        );
        entity.setPurchaseAmount(balance.sourceAmount());
        entity.setPaymentAmount(balance.settledAmount());
        entity.setClosingAmount(balance.closingAmount());
    }
}
