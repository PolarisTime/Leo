package com.leo.erp.finance.overview.repository;

import java.time.LocalDate;

public record FinanceOverviewFilter(
        Long settlementCompanyId,
        LocalDate asOfDate,
        String direction,
        String counterpartyType,
        String keyword,
        boolean onlyOpen
) {
}
