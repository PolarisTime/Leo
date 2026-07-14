package com.leo.erp.finance.cashledger.web.dto;

import java.math.BigDecimal;

public record CashLedgerSummaryResponse(
        BigDecimal openingBalance,
        BigDecimal periodIncome,
        BigDecimal periodExpense,
        BigDecimal closingBalance
) {
}
