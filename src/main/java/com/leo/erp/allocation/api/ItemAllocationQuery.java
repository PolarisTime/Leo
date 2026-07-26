package com.leo.erp.allocation.api;

import java.util.Collection;
import java.util.List;

/** 跨业务模块读取明细分配汇总的同步查询接口。 */
public interface ItemAllocationQuery {

    List<ItemAllocationSummary> summarizeSalesByPurchaseOrderItemIds(Collection<Long> purchaseOrderItemIds);

    List<ItemAllocationSummary> summarizeSalesByInboundItemIds(Collection<Long> inboundItemIds);
}
