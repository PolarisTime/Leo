package com.leo.erp.finance.payment.web.dto;

import java.math.BigDecimal;

public record PaymentAllocationResponse(
        Long id,
        Integer lineNo,
        Long sourceStatementId,
        Long sourceSupplierStatementId,
        Long sourceFreightStatementId,
        String statementNo,
        BigDecimal statementBalanceAmount,
        BigDecimal allocatedAmount
) {
    public PaymentAllocationResponse(Long id,
                                     Integer lineNo,
                                     Long sourceStatementId,
                                     String statementNo,
                                     BigDecimal statementBalanceAmount,
                                     BigDecimal allocatedAmount) {
        this(id, lineNo, sourceStatementId, null, null, statementNo, statementBalanceAmount, allocatedAmount);
    }
}
