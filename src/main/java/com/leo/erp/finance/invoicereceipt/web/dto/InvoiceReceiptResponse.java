package com.leo.erp.finance.invoicereceipt.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceReceiptResponse(
        Long id,
        String receiveNo,
        String invoiceNo,
        String sourcePurchaseOrderNos,
        String supplierName,
        String invoiceTitle,
        LocalDate invoiceDate,
        String invoiceType,
        BigDecimal amount,
        BigDecimal taxAmount,
        String status,
        String operatorName,
        String remark,
        List<InvoiceReceiptItemResponse> items
) {
}
