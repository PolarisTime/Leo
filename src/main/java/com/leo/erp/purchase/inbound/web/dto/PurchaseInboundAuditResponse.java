package com.leo.erp.purchase.inbound.web.dto;

import com.leo.erp.purchase.order.web.dto.PurchaseOrderCompletionResponse;

public record PurchaseInboundAuditResponse(
        PurchaseInboundResponse purchaseInbound,
        PurchaseOrderCompletionResponse purchaseOrderCompletion
) {
}
