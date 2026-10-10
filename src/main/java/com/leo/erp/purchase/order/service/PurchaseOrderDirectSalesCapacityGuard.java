package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.api.PurchaseOrderSalesAllocation;
import com.leo.erp.purchase.api.PurchaseOrderSalesAllocationQuery;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购订单「直接销售占用量 ≤ 已入库量」不变式校验。
 *
 * <p>采购订单完成(入库审核自动完成, 或人工强制结单)意味着不再有后续入库; 此时若某个明细行
 * 已被销售订单直接占用(不经过采购入库的直连销售)的件数超过最终入库量, 等于把不存在的货卖了
 * 出去, 必须拒绝完成并提示先处理销售端。</p>
 */
@Service
public class PurchaseOrderDirectSalesCapacityGuard {

    private final PurchaseOrderSalesAllocationQuery salesAllocationQuery;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;

    public PurchaseOrderDirectSalesCapacityGuard(PurchaseOrderSalesAllocationQuery salesAllocationQuery,
                                                 PurchaseInboundItemQueryService purchaseInboundItemQueryService) {
        this.salesAllocationQuery = salesAllocationQuery;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
    }

    /** 累计有效入库件数: 仅未删除且状态为已审核/完成入库的入库单, 草稿不参与。 */
    public Map<Long, Integer> loadReceivedQuantityMap(Collection<Long> sourcePurchaseOrderItemIds) {
        if (sourcePurchaseOrderItemIds == null || sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> receivedQuantityByItemId = new HashMap<>();
        purchaseInboundItemQueryService
                .summarizeEffectiveQuantityBySourcePurchaseOrderItemIds(sourcePurchaseOrderItemIds)
                .forEach((sourceItemId, totalQuantity) -> receivedQuantityByItemId.put(
                        sourceItemId,
                        totalQuantity == null ? 0 : Math.toIntExact(totalQuantity)
                ));
        return receivedQuantityByItemId;
    }

    /**
     * 按已入库件数校验直接销售占用; {@code receivedQuantityByItemId} 由调用方传入以避免重复汇总。
     */
    public void assertCovered(Collection<Long> sourcePurchaseOrderItemIds,
                              Map<Long, Integer> receivedQuantityByItemId) {
        if (salesAllocationQuery == null
                || sourcePurchaseOrderItemIds == null
                || sourcePurchaseOrderItemIds.isEmpty()) {
            return;
        }
        List<Long> itemIds = List.copyOf(sourcePurchaseOrderItemIds);
        for (PurchaseOrderSalesAllocation summary : salesAllocationQuery.summarizeByPurchaseOrderItemIds(itemIds)) {
            long inboundQuantity = receivedQuantityByItemId.getOrDefault(
                    summary.sourcePurchaseOrderItemId(),
                    0
            );
            long directSalesQuantity = summary.totalQuantity() == null ? 0L : summary.totalQuantity();
            if (directSalesQuantity > inboundQuantity) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源采购明细 " + summary.sourcePurchaseOrderItemId()
                                + " 的历史直连销售数量超过最终入库量：已入库 " + inboundQuantity
                                + " 件，已占用 " + directSalesQuantity + " 件，请先处理历史销售订单"
                );
            }
        }
    }

    /** 一次性完成两步: 汇总已入库件数并校验直接销售占用。 */
    public void assertCovered(Collection<Long> sourcePurchaseOrderItemIds) {
        assertCovered(sourcePurchaseOrderItemIds, loadReceivedQuantityMap(sourcePurchaseOrderItemIds));
    }
}
