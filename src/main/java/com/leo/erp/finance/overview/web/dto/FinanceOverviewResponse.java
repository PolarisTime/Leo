package com.leo.erp.finance.overview.web.dto;

import com.leo.erp.common.api.PageResponse;

import java.time.LocalDate;

public record FinanceOverviewResponse(
        LocalDate asOfDate,
        FinanceOverviewSummaryResponse summary,
        PageResponse<FinanceBalanceResponse> balances
) {
}
