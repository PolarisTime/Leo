package com.leo.erp.purchase.api;

import java.util.Collection;

public interface PurchaseOrderPrepaymentSourceQuery {

    PurchaseOrderPrepaymentSnapshot lockAndRequire(
            Long targetPurchaseOrderId,
            Collection<Long> affectedPurchaseOrderIds
    );
}
