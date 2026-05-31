package com.leo.erp.finance.receivablepayable.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivablePayableDetailItemResponse(
        String id,
        Long statementId,
        String statementNo,
        String sourceNo,
        String projectName,
        LocalDate businessDate,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal currentAmount,
        BigDecimal statementSettledAmount,
        BigDecimal statementBalanceAmount,
        String status,
        String remark
) {
}
