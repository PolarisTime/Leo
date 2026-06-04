package com.leo.erp.finance.ledgeradjustment.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerAdjustmentResponse(
        Long id,
        String adjustmentNo,
        String direction,
        String counterpartyType,
        String counterpartyCode,
        String counterpartyName,
        Long projectId,
        String projectName,
        LocalDate adjustmentDate,
        BigDecimal amount,
        String adjustmentType,
        String effect,
        String status,
        String operatorName,
        String remark
) {
}
