package com.leo.erp.statement.customer.web.dto;

import java.math.BigDecimal;

public record CustomerStatementSummaryResponse(
        long documentCount,
        BigDecimal salesAmount,
        BigDecimal receiptAmount,
        BigDecimal closingAmount
) {
}
