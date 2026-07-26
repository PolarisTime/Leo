package com.leo.erp.purchase.api;

import java.util.Collection;

public interface PurchaseOrderPrepaymentReferenceGuard {

    void assertNoActivePrepayment(
            Long sourcePurchaseOrderId,
            Collection<Long> sourcePurchaseOrderItemIds,
            String operationName
    );
}
