package com.leo.erp.purchase.order.web.dto;

public record PurchaseOrderCompletionResponse(
        Long purchaseOrderId,
        String purchaseOrderNo,
        String status
) {
}
