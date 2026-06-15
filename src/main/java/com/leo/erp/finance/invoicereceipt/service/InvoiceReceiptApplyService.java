package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.InvoiceAmountCalculator;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

@Service
public class InvoiceReceiptApplyService {

    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final InvoiceReceiptSourceService invoiceReceiptSourceService;
    private final InvoiceAmountCalculator amountCalculator;

    public InvoiceReceiptApplyService(WorkflowTransitionGuard workflowTransitionGuard,
                                      InvoiceReceiptSourceService invoiceReceiptSourceService,
                                      InvoiceAmountCalculator amountCalculator) {
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.invoiceReceiptSourceService = invoiceReceiptSourceService;
        this.amountCalculator = amountCalculator;
    }

    void apply(InvoiceReceipt entity, InvoiceReceiptRequest request, LongSupplier nextIdSupplier) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "收票单状态",
                StatusConstants.ALLOWED_INVOICE_RECEIPT_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "invoice-receipt",
                entity.getStatus(),
                nextStatus,
                StatusConstants.INVOICE_RECEIVED
        );
        entity.setReceiveNo(request.receiveNo());
        entity.setInvoiceNo(request.invoiceNo());
        entity.setSupplierName(request.supplierName());
        entity.setInvoiceTitle(request.invoiceTitle() == null || request.invoiceTitle().isBlank()
                ? request.supplierName()
                : request.invoiceTitle().trim());
        entity.setInvoiceDate(request.invoiceDate());
        entity.setInvoiceType(request.invoiceType());
        entity.setStatus(nextStatus);
        entity.setOperatorName(request.operatorName());
        entity.setRemark(request.remark());

        BigDecimal amount = invoiceReceiptSourceService.applyItems(
                entity,
                request.items(),
                request.supplierName(),
                nextIdSupplier
        );
        InvoiceAmountCalculator.InvoiceAmounts amounts = amountCalculator.resolve(
                "收票",
                amount,
                request.amount(),
                request.taxAmount()
        );
        entity.setAmount(amounts.amount());
        entity.setTaxAmount(amounts.taxAmount());
    }
}
