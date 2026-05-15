package com.leo.erp.finance.projectar.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectArDetailRowResponse(
        String sourceDocumentNo,
        String documentType,
        LocalDate businessDate,
        String customerCode,
        String customerName,
        BigDecimal amount,
        BigDecimal writtenOffAmount,
        BigDecimal unwrittenOffAmount,
        String reconciliationStatus,
        String receiptStatus,
        String operatorName,
        String remark
) {
}
