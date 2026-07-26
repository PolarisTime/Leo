package com.leo.erp.statement.customer.repository;

import java.math.BigDecimal;

public record CustomerStatementSummaryAggregate(
        long documentCount,
        BigDecimal salesAmount,
        BigDecimal receiptAmount,
        BigDecimal closingAmount
) {
}
