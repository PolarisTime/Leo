package com.leo.erp.finance.overview.web.dto;

import java.math.BigDecimal;

public record FinanceBalanceResponse(
        String direction,
        String counterpartyType,
        Long counterpartyId,
        String counterpartyCode,
        String counterpartyName,
        Long settlementCompanyId,
        String settlementCompanyName,
        BigDecimal recognizedAmount,
        BigDecimal settledAmount,
        BigDecimal outstandingAmount,
        BigDecimal advanceAmount
) {
}
