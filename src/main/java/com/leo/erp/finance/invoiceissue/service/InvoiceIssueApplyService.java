package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.InvoiceAmountCalculator;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class InvoiceIssueApplyService {

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final InvoiceIssueSourceService invoiceIssueSourceService;
    private final InvoiceAmountCalculator amountCalculator;

    public InvoiceIssueApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                                    InvoiceIssueSourceService invoiceIssueSourceService,
                                    InvoiceAmountCalculator amountCalculator) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.invoiceIssueSourceService = invoiceIssueSourceService;
        this.amountCalculator = amountCalculator;
    }

    void apply(InvoiceIssue entity, InvoiceIssueRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "开票单状态",
                StatusConstants.ALLOWED_INVOICE_ISSUE_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "invoice-issue",
                entity.getStatus(),
                nextStatus,
                StatusConstants.ISSUED
        );
        entity.setIssueNo(request.issueNo());
        entity.setInvoiceNo(request.invoiceNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setInvoiceDate(request.invoiceDate());
        entity.setInvoiceType(request.invoiceType());
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());

        InvoiceIssueSourceService.SourceApplyResult sourceResult = invoiceIssueSourceService.applyItems(
                entity,
                request.items(),
                request.customerName(),
                request.projectName(),
                nextIdSupplier
        );
        InvoiceAmountCalculator.InvoiceAmounts amounts = amountCalculator.resolve(
                "开票",
                sourceResult.amount(),
                request.amount(),
                request.taxAmount()
        );
        entity.setAmount(amounts.amount());
        entity.setTaxAmount(amounts.taxAmount());
        entity.setSettlementCompanyId(sourceResult.settlementCompanyId());
        entity.setSettlementCompanyName(sourceResult.settlementCompanyName());
    }
}
