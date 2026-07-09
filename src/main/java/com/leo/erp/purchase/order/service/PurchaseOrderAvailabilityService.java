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
    private final PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService;

    public PurchaseOrderAvailabilityService(PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                            ItemAllocationNativeRepository itemAllocationRepo,
                                            PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService) {
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.itemAllocationRepo = itemAllocationRepo;
        this.purchaseOrderItemPieceWeightService = purchaseOrderItemPieceWeightService;
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

    Map<Long, BigDecimal> loadSalesRemainingWeightMap(PurchaseOrder order) {
        List<Long> orderItemIds = orderItemIds(order);
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseOrderItemPieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(orderItemIds);
    }

    Map<Long, BigDecimal> loadLockedSalesWeightMap(PurchaseOrder order) {
        List<Long> orderItemIds = orderItemIds(order);
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseOrderItemPieceWeightService.summarizeLockedSalesWeightByPurchaseOrderItemIds(orderItemIds);
    }

    Integer remainingQuantity(PurchaseOrderItem item, Map<Long, Integer> allocatedQuantityMap) {
        int allocatedQuantity = allocatedQuantityMap.getOrDefault(item.getId(), 0);
        return Math.max(0, item.getQuantity() - allocatedQuantity);
    }

    BigDecimal salesRemainingWeightTon(PurchaseOrderItem item,
                                       Map<Long, Integer> allocatedQuantityMap,
                                       Map<Long, BigDecimal> remainingWeightMap) {
        BigDecimal remainingWeightTon = remainingWeightMap.get(item.getId());
        if (remainingWeightTon != null) {
            return remainingWeightTon;
        }
        int remainingQuantity = remainingQuantity(item, allocatedQuantityMap);
        if (remainingQuantity == item.getQuantity()) {
            return TradeItemCalculator.scaleWeightTon(item.getWeightTon());
        }
        return TradeItemCalculator.calculateWeightTon(remainingQuantity, item.getPieceWeightTon());
    }

    BigDecimal lockedSalesWeightTon(PurchaseOrderItem item, Map<Long, BigDecimal> lockedSalesWeightMap) {
        return TradeItemCalculator.scaleWeightTon(
                lockedSalesWeightMap.getOrDefault(item.getId(), BigDecimal.ZERO)
        );
    }

    Map<Long, Integer> buildImportableQuantityMap(List<PurchaseOrder> orders, ImportCandidateUsage usage) {
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
                    purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(itemIds)
            );
            case SALES_ORDER -> toIntegerQuantityMap(
                    itemAllocationRepo.summarizeSalesByPurchaseOrderItems(itemIds, null)
                            .stream().collect(Collectors.toMap(
                                    ItemAllocationNativeRepository.AllocationProjection::getSourceItemId,
                                    p -> p.getTotalQuantity()
                            ))
            );
        };

        Map<Long, Integer> result = new HashMap<>();
        for (PurchaseOrder order : orders) {
            int importableQuantity = order.getItems().stream()
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
