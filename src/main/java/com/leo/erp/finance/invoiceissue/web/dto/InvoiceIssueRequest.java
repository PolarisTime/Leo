package com.leo.erp.finance.invoiceissue.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceIssueRequest(
        String issueNo,
        @jakarta.validation.constraints.NotBlank(message = "发票号码不能为空")
        String invoiceNo,
        @jakarta.validation.constraints.NotBlank(message = "客户不能为空")
        String customerName,
        @jakarta.validation.constraints.NotBlank(message = "项目不能为空")
        String projectName,
        Long settlementCompanyId,
        String settlementCompanyName,
        @NotNull(message = "发票日期不能为空")
        LocalDate invoiceDate,
        @jakarta.validation.constraints.NotBlank(message = "发票类型不能为空")
        String invoiceType,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.00", message = "金额不能小于0")
        BigDecimal amount,
        @NotNull(message = "税额不能为空")
        @DecimalMin(value = "0.00", message = "税额不能小于0")
        BigDecimal taxAmount,
        @jakarta.validation.constraints.NotBlank(message = "状态不能为空")
        String status,
        @jakarta.validation.constraints.NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark,
        @Valid @NotEmpty(message = "请至少填写一条开票明细")
        List<InvoiceIssueItemRequest> items,
        Long customerId,
        Long projectId
) {
    public InvoiceIssueRequest(String issueNo,
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
                               List<InvoiceIssueItemRequest> items) {
        this(issueNo, invoiceNo, customerName, projectName, settlementCompanyId, settlementCompanyName,
                invoiceDate, invoiceType, amount, taxAmount, status, operatorName, remark, items, null, null);
    }

    public InvoiceIssueRequest(String issueNo,
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
                               List<InvoiceIssueItemRequest> items) {
        this(issueNo, invoiceNo, customerName, projectName, null, null, invoiceDate, invoiceType,
                amount, taxAmount, status, operatorName, remark, items, null, null);
    }
}
