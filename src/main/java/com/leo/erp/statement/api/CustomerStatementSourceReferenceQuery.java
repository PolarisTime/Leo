package com.leo.erp.statement.api;

import java.util.Collection;
import java.util.List;

public interface CustomerStatementSourceReferenceQuery {

    List<Long> findActiveStatementIdsBySalesOrderItemIds(Collection<Long> salesOrderItemIds);
}
