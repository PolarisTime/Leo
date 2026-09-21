package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.api.ItemAllocationQuery;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderAvailabilityService {

    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final ItemAllocationQuery itemAllocationQuery;
    public PurchaseOrderAvailabilityService(PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                            ItemAllocationQuery itemAllocationQuery) {
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.itemAllocationQuery = itemAllocationQuery;
    }

    Map<Long, Integer> loadInboundAllocatedQuantityMap(PurchaseOrder order) {
        List<Long> orderItemIds = orderItemIds(order);
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> summaryMap = purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(orderItemIds);
        Map<Long, Integer> allocatedMap = new HashMap<>();
        summaryMap.forEach((key, value) -> allocatedMap.put(key, Math.toIntExact(value)));
        return allocatedMap;
    }

    Map<Long, Integer> loadSalesAllocatedQuantityMap(PurchaseOrder order) {
        List<Long> orderItemIds = orderItemIds(order);
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> allocatedMap = new HashMap<>();
        itemAllocationQuery.summarizeSalesByPurchaseOrderItemIds(orderItemIds)
                .forEach(summary -> allocatedMap.put(
                        summary.sourceItemId(),
                        Math.toIntExact(summary.totalQuantity())
                ));
        return allocatedMap;
    }

    Integer remainingQuantity(PurchaseOrderItem item, Map<Long, Integer> allocatedQuantityMap) {
        int allocatedQuantity = allocatedQuantityMap.getOrDefault(item.getId(), 0);
        return Math.max(0, item.getQuantity() - allocatedQuantity);
    }

    BigDecimal salesRemainingWeightTon(PurchaseOrderItem item,
                                       Map<Long, Integer> allocatedQuantityMap) {
        int remainingQuantity = remainingQuantity(item, allocatedQuantityMap);
        if (remainingQuantity == item.getQuantity()) {
            return TradeItemCalculator.scaleWeightTon(item.getWeightTon());
        }
        return TradeItemCalculator.calculateWeightTon(remainingQuantity, item.getPieceWeightTon());
    }

    Map<Long, Integer> buildInboundImportableQuantityMap(
            List<PurchaseOrder> orders,
            Long currentRecordId
    ) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        List<PurchaseOrderItem> items = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .toList();
        List<Long> itemIds = items.stream()
                .map(PurchaseOrderItem::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (itemIds.isEmpty()) {
            return orders.stream().collect(Collectors.toMap(PurchaseOrder::getId, order -> 0));
        }

        Map<Long, Integer> allocatedQuantityMap = toIntegerQuantityMap(
                currentRecordId == null
                        ? purchaseInboundItemQueryService
                            .summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(itemIds)
                        : purchaseInboundItemQueryService
                            .summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                                    itemIds,
                                    currentRecordId
                            )
        );

        Map<Long, Integer> result = new HashMap<>();
        for (PurchaseOrder order : orders) {
            // 行级剩余量: 只要存在剩余行即为候选; 跨行总量用 long 累加,
            // int 相加会在超大数量时回绕为负数, 导致订单被错误过滤。
            long importableQuantity = order.getItems().stream()
                    .filter(item -> item.getQuantity() != null && item.getQuantity() >= 1)
                    .mapToLong(item -> remainingQuantity(item, allocatedQuantityMap))
                    .sum();
            result.put(order.getId(), saturateToInt(importableQuantity));
        }
        return result;
    }

    /** 总量超出 int 时收敛到 {@link Integer#MAX_VALUE}, 避免回绕成负数被过滤。 */
    private static int saturateToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private List<Long> orderItemIds(PurchaseOrder order) {
        return order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .distinct()
                .toList();
    }

    /** 占用总量超出 int 时收敛到 {@link Integer#MAX_VALUE}, 保证剩余量为 0 而不是溢出抛错。 */
    private Map<Long, Integer> toIntegerQuantityMap(Map<Long, Long> source) {
        Map<Long, Integer> target = new HashMap<>();
        if (source == null || source.isEmpty()) {
            return target;
        }
        source.forEach((key, value) -> target.put(
                key,
                value == null ? 0 : saturateToInt(value)
        ));
        return target;
    }

}
