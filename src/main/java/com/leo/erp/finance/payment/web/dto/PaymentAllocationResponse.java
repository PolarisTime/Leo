package com.leo.erp.finance.payment.web.dto;

import java.math.BigDecimal;

public record PaymentAllocationResponse(
        Long id,
        Integer lineNo,
        Long sourceStatementId,
        Long sourceFreightStatementId,
        String statementNo,
        BigDecimal statementBalanceAmount,
        BigDecimal allocatedAmount
) {
}
