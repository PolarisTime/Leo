package com.leo.erp.statement.freight.web.dto;

import java.math.BigDecimal;

public record FreightStatementSummaryResponse(
        long documentCount,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount
) {
}
