package com.leo.erp.sales.api;

import java.util.Collection;
import java.util.List;

public interface SalesOrderLogisticsSourceQuery {

    List<SalesOrderSourceSnapshot> findByOrderIds(Collection<Long> orderIds);

    List<SalesOrderSourceSnapshot> findBySourceItemIds(Collection<Long> sourceItemIds);
}
