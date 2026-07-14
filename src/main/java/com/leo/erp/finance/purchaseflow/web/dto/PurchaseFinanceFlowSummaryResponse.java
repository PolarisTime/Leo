package com.leo.erp.finance.purchaseflow.web.dto;

import java.math.BigDecimal;

public record PurchaseFinanceFlowSummaryResponse(
        BigDecimal purchasePlanAmount,
        BigDecimal inboundSettlementAmount,
        BigDecimal reconciledAmount,
        BigDecimal invoicedAmount,
        BigDecimal expenseAmount,
        BigDecimal incomeAmount,
        BigDecimal netCashExpense,
        BigDecimal historicalAdjustmentAmount,
        BigDecimal payableBalance,
        BigDecimal prepaymentBalance
) {
}
