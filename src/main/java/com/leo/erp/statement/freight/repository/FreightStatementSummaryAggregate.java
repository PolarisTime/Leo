package com.leo.erp.statement.freight.repository;

import java.math.BigDecimal;

public record FreightStatementSummaryAggregate(
        long documentCount,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount
) {
}
