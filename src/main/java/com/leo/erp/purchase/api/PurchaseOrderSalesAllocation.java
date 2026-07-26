package com.leo.erp.purchase.api;

public record PurchaseOrderSalesAllocation(
        Long sourcePurchaseOrderItemId,
        Long totalQuantity
) {
}
