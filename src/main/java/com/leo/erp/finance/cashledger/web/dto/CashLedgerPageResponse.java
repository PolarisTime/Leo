package com.leo.erp.finance.cashledger.web.dto;

import com.leo.erp.common.api.PageResponse;

public record CashLedgerPageResponse(
        CashLedgerSummaryResponse summary,
        PageResponse<CashLedgerLineResponse> page
) {
}
