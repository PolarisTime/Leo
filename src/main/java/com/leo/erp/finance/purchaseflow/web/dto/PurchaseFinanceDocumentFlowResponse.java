package com.leo.erp.finance.purchaseflow.web.dto;

import com.leo.erp.common.api.PageResponse;

public record PurchaseFinanceDocumentFlowResponse(
        PurchaseFinanceFlowSummaryResponse summary,
        PageResponse<PurchaseFinanceFlowLineResponse> items
) {
}
