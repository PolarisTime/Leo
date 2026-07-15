package com.leo.erp.allocation.appservice;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Application Service：采购明细重量分配操作的抽象接口。
 * 实现类由 purchase 模块提供。
 */
public interface PurchaseItemPieceWeightAppService {

    /** 为销售订单明细分配采购明细重量，返回分配到的重量(吨) */
    BigDecimal allocateForSalesOrderItem(
            Long sourcePurchaseOrderItemId,
            Integer quantity,
            Long salesOrderItemId,
            int lineNo);

    /** 按销售订单导入的采购入库明细锁定对应采购逐件重量。 */
    BigDecimal allocateForInboundSourceSalesOrderItem(
            Long sourceInboundItemId,
            Integer quantity,
            Long salesOrderItemId,
            int lineNo);

    /** 释放已分配给指定销售订单明细的采购明细 */
    void releaseSalesOrderItems(Collection<Long> salesOrderItemIds);

    /** 查询采购订单明细的剩余可分配重量 */
    Map<Long, BigDecimal> summarizeRemainingWeightByPurchaseOrderItemIds(Collection<Long> purchaseOrderItemIds);
}
