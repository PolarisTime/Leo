package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivablePayableDetailItemResponse(
        String id,
        String entryRole,
        String sourceType,
        Long sourceDocumentId,
        String documentNo,
        String sourceNo,
        String projectName,
        LocalDate accountingDate,
        LocalDate dueDate,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        BigDecimal balanceAmount,
        Integer ageDays,
        String status,
        String remark
) {
}
