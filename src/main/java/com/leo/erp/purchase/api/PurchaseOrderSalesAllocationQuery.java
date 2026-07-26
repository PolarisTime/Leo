package com.leo.erp.purchase.api;

import java.util.Collection;
import java.util.List;

public interface PurchaseOrderSalesAllocationQuery {

    List<PurchaseOrderSalesAllocation> summarizeByPurchaseOrderItemIds(
            Collection<Long> purchaseOrderItemIds
    );
}
