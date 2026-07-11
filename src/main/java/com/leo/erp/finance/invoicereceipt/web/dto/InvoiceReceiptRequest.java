package com.leo.erp.finance.invoicereceipt.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceReceiptRequest(
        String receiveNo,
        @jakarta.validation.constraints.NotBlank(message = "发票号码不能为空")
        String invoiceNo,
        String supplierCode,
        @jakarta.validation.constraints.NotBlank(message = "供应商不能为空")
        String supplierName,
        String invoiceTitle,
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
        @Valid @NotEmpty(message = "请至少填写一条收票明细")
        List<InvoiceReceiptItemRequest> items
) {
    public InvoiceReceiptRequest(String receiveNo,
                                 String invoiceNo,
                                 String supplierName,
                                 String invoiceTitle,
                                 LocalDate invoiceDate,
                                 String invoiceType,
                                 BigDecimal amount,
                                 BigDecimal taxAmount,
                                 String status,
                                 String operatorName,
                                 String remark,
                                 List<InvoiceReceiptItemRequest> items) {
        this(receiveNo, invoiceNo, null, supplierName, invoiceTitle, invoiceDate, invoiceType,
                amount, taxAmount, status, operatorName, remark, items);
    }
}
