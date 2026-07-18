package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
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
    private final ItemAllocationNativeRepository itemAllocationRepo;
    public PurchaseOrderAvailabilityService(PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                            ItemAllocationNativeRepository itemAllocationRepo) {
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.itemAllocationRepo = itemAllocationRepo;
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
        itemAllocationRepo.summarizeSalesByPurchaseOrderItems(orderItemIds, null)
                .forEach(p -> allocatedMap.put(p.getSourceItemId(), Math.toIntExact(p.getTotalQuantity())));
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

    Map<Long, Integer> buildImportableQuantityMap(List<PurchaseOrder> orders, ImportCandidateUsage usage) {
        return buildImportableQuantityMap(orders, usage, null);
    }

    Map<Long, Integer> buildImportableQuantityMap(
            List<PurchaseOrder> orders,
            ImportCandidateUsage usage,
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

        Map<Long, Integer> allocatedQuantityMap = switch (usage) {
            case PURCHASE_INBOUND -> toIntegerQuantityMap(
                    currentRecordId == null
                            ? purchaseInboundItemQueryService
                            .summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(itemIds)
                            : purchaseInboundItemQueryService
                            .summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                                    itemIds,
                                    currentRecordId
                            )
            );
            case SALES_ORDER -> toIntegerQuantityMap(
                    itemAllocationRepo.summarizeSalesByPurchaseOrderItems(itemIds, currentRecordId)
                            .stream().collect(Collectors.toMap(
                                    ItemAllocationNativeRepository.AllocationProjection::getSourceItemId,
                                    p -> p.getTotalQuantity()
                            ))
            );
        };

        Map<Long, Integer> result = new HashMap<>();
        for (PurchaseOrder order : orders) {
            boolean hasInboundAllocation = usage == ImportCandidateUsage.PURCHASE_INBOUND
                    && order.getItems().stream().anyMatch(
                    item -> allocatedQuantityMap.getOrDefault(item.getId(), 0) > 0
            );
            int importableQuantity = hasInboundAllocation
                    ? 0
                    : order.getItems().stream()
                    .mapToInt(item -> remainingQuantity(item, allocatedQuantityMap))
                    .sum();
            result.put(order.getId(), importableQuantity);
        }
        return result;
    }

    private List<Long> orderItemIds(PurchaseOrder order) {
        return order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .distinct()
                .toList();
    }

    private Map<Long, Integer> toIntegerQuantityMap(Map<Long, Long> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> target = new HashMap<>();
        source.forEach((key, value) -> target.put(key, Math.toIntExact(value)));
        return target;
    }

    enum ImportCandidateUsage {
        PURCHASE_INBOUND("purchase-inbound"),
        SALES_ORDER("sales-order");

        private final String value;

        ImportCandidateUsage(String value) {
            this.value = value;
        }

        static ImportCandidateUsage from(String value) {
            for (ImportCandidateUsage usage : values()) {
                if (usage.value.equalsIgnoreCase(value == null ? "" : value.trim())) {
                    return usage;
                }
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "usage 不支持当前导入场景");
        }
    }
}
