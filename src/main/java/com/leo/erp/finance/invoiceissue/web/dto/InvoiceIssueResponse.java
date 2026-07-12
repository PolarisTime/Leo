package com.leo.erp.finance.invoiceissue.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceIssueResponse(
        Long id,
        String issueNo,
        String invoiceNo,
        String customerName,
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate invoiceDate,
        String invoiceType,
        BigDecimal amount,
        BigDecimal taxAmount,
        String status,
        boolean deletedFlag,
        String operatorName,
        String remark,
        List<InvoiceIssueItemResponse> items,
        Long customerId,
        Long projectId
) {
    public InvoiceIssueResponse(Long id,
                                String issueNo,
                                String invoiceNo,
                                String customerName,
                                String projectName,
                                Long settlementCompanyId,
                                String settlementCompanyName,
                                LocalDate invoiceDate,
                                String invoiceType,
                                BigDecimal amount,
                                BigDecimal taxAmount,
                                String status,
                                boolean deletedFlag,
                                String operatorName,
                                String remark,
                                List<InvoiceIssueItemResponse> items) {
        this(id, issueNo, invoiceNo, customerName, projectName, settlementCompanyId, settlementCompanyName,
                invoiceDate, invoiceType, amount, taxAmount, status, deletedFlag, operatorName, remark, items,
                null, null);
    }

    public InvoiceIssueResponse(Long id,
                                String issueNo,
                                String invoiceNo,
                                String customerName,
                                String projectName,
                                Long settlementCompanyId,
                                String settlementCompanyName,
                                LocalDate invoiceDate,
                                String invoiceType,
                                BigDecimal amount,
                                BigDecimal taxAmount,
                                String status,
                                String operatorName,
                                String remark,
                                List<InvoiceIssueItemResponse> items) {
        this(id, issueNo, invoiceNo, customerName, projectName, settlementCompanyId, settlementCompanyName,
                invoiceDate, invoiceType, amount, taxAmount, status, false, operatorName, remark, items, null, null);
    }

    public InvoiceIssueResponse(Long id,
                                String issueNo,
                                String invoiceNo,
                                String customerName,
                                String projectName,
                                LocalDate invoiceDate,
                                String invoiceType,
                                BigDecimal amount,
                                BigDecimal taxAmount,
                                String status,
                                String operatorName,
                                String remark,
                                List<InvoiceIssueItemResponse> items) {
        this(id, issueNo, invoiceNo, customerName, projectName, null, null, invoiceDate, invoiceType,
                amount, taxAmount, status, false, operatorName, remark, items, null, null);
    }
}
