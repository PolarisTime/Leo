package com.leo.erp.purchase.api;

import java.util.Collection;
import java.util.Set;

public interface PurchaseOrderReferenceGuard {

    boolean hasActivePurchaseOrderItemReferences(Collection<Long> purchaseOrderItemIds);

    boolean hasActiveInboundItemReferences(Collection<Long> inboundItemIds);

    /**
     * 返回仍被销售订单明细物理引用（不区分销售订单是否已软删除）的采购入库明细 ID。
     * 用于物理释放入库明细前判断哪些行不能删除。
     */
    Set<Long> findReferencedInboundItemIds(Collection<Long> inboundItemIds);
}
