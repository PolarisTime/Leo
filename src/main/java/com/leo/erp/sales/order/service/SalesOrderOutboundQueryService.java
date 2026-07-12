package com.leo.erp.sales.order.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface SalesOrderOutboundQueryService {

    List<OutboundRecord> findAuditedOutboundsBySourceSalesOrderItemIds(
            Collection<Long> sourceSalesOrderItemIds
    );

    record OutboundRecord(
            String salesOrderNo,
            String status,
            List<OutboundItemRecord> items
    ) {
    }

    record OutboundItemRecord(
            Long sourceSalesOrderItemId,
            Integer quantity,
            BigDecimal weightTon
    ) {
    }
}
