package com.leo.erp.sales.order.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

public interface SalesOrderOutboundPricingSyncService {

    void syncAuditedOutboundPricing(Collection<Long> sourceSalesOrderItemIds,
                                    Map<Long, BigDecimal> unitPriceByItemId);
}
