package com.leo.erp.finance.invoiceissue.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceIssueResponse(
        Long id,
        String issueNo,
        String invoiceNo,
        String sourceSalesOrderNos,
        String customerName,
        String projectName,
        LocalDate invoiceDate,
        String invoiceType,
        BigDecimal amount,
        BigDecimal taxAmount,
        String status,
        String operatorName,
        String remark,
        List<InvoiceIssueItemResponse> items
) {
}
