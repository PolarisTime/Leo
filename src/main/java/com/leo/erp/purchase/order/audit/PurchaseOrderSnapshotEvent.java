package com.leo.erp.purchase.order.audit;

import com.leo.erp.system.operationlog.event.BusinessOperationEvent;

public record PurchaseOrderSnapshotEvent(
        BusinessOperationEvent operation,
        PurchaseOrderAuditSnapshot snapshot
) {
}
