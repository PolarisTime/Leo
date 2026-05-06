package com.leo.erp.finance.receipt.web.dto;

import java.math.BigDecimal;

public record ReceiptAllocationResponse(
        Long id,
        Integer lineNo,
        Long sourceStatementId,
        String statementNo,
        String projectName,
        BigDecimal statementBalanceAmount,
        BigDecimal allocatedAmount
) {
}
