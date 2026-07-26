package com.leo.erp.sales.api;

import java.util.Collection;

public interface SalesOrderStatementReferenceQuery {

    boolean hasActiveCustomerStatementReferences(Collection<Long> salesOrderItemIds);
}
