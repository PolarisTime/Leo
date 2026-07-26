package com.leo.erp.purchase.api;

import java.util.Collection;

public interface PurchaseOrderReferenceGuard {

    boolean hasActivePurchaseOrderItemReferences(Collection<Long> purchaseOrderItemIds);

    boolean hasActiveInboundItemReferences(Collection<Long> inboundItemIds);
}
