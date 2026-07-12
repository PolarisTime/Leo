package com.leo.erp.finance.receipt.web.dto;

import java.math.BigDecimal;

public record ReceiptAllocationResponse(
        Long id,
        Integer lineNo,
        Long sourceCustomerStatementId,
        String statementNo,
        String projectName,
        BigDecimal statementBalanceAmount,
        BigDecimal allocatedAmount
) {
    @Deprecated(forRemoval = false)
    public Long sourceStatementId() {
        return sourceCustomerStatementId;
    }
}
