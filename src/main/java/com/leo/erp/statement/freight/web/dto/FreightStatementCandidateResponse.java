package com.leo.erp.statement.freight.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FreightStatementCandidateResponse(
        Long id,
        String billNo,
        String carrierName,
        String customerName,
        String projectName,
        LocalDate billTime,
        BigDecimal totalWeight,
        BigDecimal totalFreight,
        String status
) {
}
