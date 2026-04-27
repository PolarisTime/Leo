package com.leo.erp.finance.receipt.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptResponse(
        Long id,
        String receiptNo,
        String customerName,
        String projectName,
        Long sourceStatementId,
        LocalDate receiptDate,
        String payType,
        BigDecimal amount,
        String status,
        String operatorName,
        String remark
) {
}
