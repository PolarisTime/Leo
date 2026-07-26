package com.leo.erp.sales.api;

import java.util.Collection;

public interface SalesOrderReceiptReferenceQuery {

    boolean hasAuditedReceiptReferences(Collection<Long> salesOrderItemIds);
}
