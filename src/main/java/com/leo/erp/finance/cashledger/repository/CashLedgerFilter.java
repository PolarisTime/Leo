package com.leo.erp.finance.cashledger.repository;

import java.time.LocalDate;

public record CashLedgerFilter(
        Long settlementCompanyId,
        LocalDate startDate,
        LocalDate endDate,
        String counterpartyType,
        Long counterpartyId,
        String flowType,
        String keyword
) {
}
