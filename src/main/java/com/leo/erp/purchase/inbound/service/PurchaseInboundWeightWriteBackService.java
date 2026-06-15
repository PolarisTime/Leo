package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundWeightWriteBackService {

    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService;

    public PurchaseInboundWeightWriteBackService(PurchaseInboundItemRepository purchaseInboundItemRepository,
                                                 PurchaseOrderRepository purchaseOrderRepository,
                                                 PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService) {
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemPieceWeightService = purchaseOrderItemPieceWeightService;
    }

    void writeBackPurchaseOrderWeights(
            List<Long> sourcePurchaseOrderItemIds,
            Long currentInboundId,
            Map<Long, SourceWeighAccumulator> currentWeighAccumulatorMap,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap
    ) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return;
        }
        Map<Long, SourceWeighAccumulator> persistedWeighAccumulatorMap = loadPersistedWeighAccumulatorMap(
                sourcePurchaseOrderItemIds, currentInboundId
        );
        Map<Long, PurchaseOrder> affectedOrderMap = loadAffectedPurchaseOrderMap(sourcePurchaseOrderItemMap);
        Map<Long, PurchaseOrderItem> writeBackItemMap = affectedOrderMap.values().stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item, (left, right) -> left));
        for (Long sourcePurchaseOrderItemId : sourcePurchaseOrderItemIds) {
            PurchaseOrderItem sourceItem = writeBackItemMap.get(sourcePurchaseOrderItemId);
            if (sourceItem == null) {
                continue;
            }
            SourceWeighAccumulator weighAccumulator = mergeWeighAccumulator(
                    persistedWeighAccumulatorMap.get(sourcePurchaseOrderItemId),
                    currentWeighAccumulatorMap.get(sourcePurchaseOrderItemId)
            );
            if (weighAccumulator != null && weighAccumulator.hasQuantity()) {
                BigDecimal averagePieceWeightTon = weighAccumulator.averagePieceWeightTon();
                BigDecimal actualWeightTon = weighAccumulator.isFullyAllocated(sourceItem.getQuantity())
                        ? TradeItemCalculator.scaleWeightTon(weighAccumulator.weightTon())
                        : TradeItemCalculator.calculateWeightTon(sourceItem.getQuantity(), averagePieceWeightTon);
                sourceItem.setWeightTon(actualWeightTon);
                sourceItem.setAmount(TradeItemCalculator.calculateAmount(actualWeightTon, sourceItem.getUnitPrice()));
                sourceItem.setActualWeightTon(actualWeightTon);
                sourceItem.setActualPieceWeightTon(TradeItemCalculator.scaleWeightTon(averagePieceWeightTon));
            } else {
                sourceItem.setActualWeightTon(null);
                sourceItem.setActualPieceWeightTon(null);
            }
        }
        affectedOrderMap.values().forEach(this::refreshPurchaseOrderTotals);
        if (!affectedOrderMap.isEmpty()) {
            purchaseOrderRepository.saveAll(affectedOrderMap.values());
            purchaseOrderItemPieceWeightService.regenerateForPurchaseOrderItems(
                    sourcePurchaseOrderItemIds.stream()
                            .map(writeBackItemMap::get)
                            .filter(item -> item != null)
                            .toList()
            );
        }
    }

    SourceWeighAccumulator mergeWeighAccumulator(SourceWeighAccumulator left, SourceWeighAccumulator right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return new SourceWeighAccumulator(
                left.quantity() + right.quantity(),
                TradeItemCalculator.scaleWeightTon(left.weightTon().add(right.weightTon()))
        );
    }

    private Map<Long, SourceWeighAccumulator> loadPersistedWeighAccumulatorMap(
            List<Long> sourcePurchaseOrderItemIds, Long currentInboundId
    ) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SourceWeighAccumulator> weighAccumulatorMap = new HashMap<>();
        purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                        sourcePurchaseOrderItemIds,
                        currentInboundId
                )
                .forEach(summary -> weighAccumulatorMap.put(
                        summary.getSourcePurchaseOrderItemId(),
                        new SourceWeighAccumulator(
                                Math.toIntExact(summary.getTotalQuantity()),
                                TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                        )
                ));
        return weighAccumulatorMap;
    }

    private Map<Long, PurchaseOrder> loadAffectedPurchaseOrderMap(
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap
    ) {
        List<Long> orderIds = sourcePurchaseOrderItemMap.values().stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(order -> order != null)
                .map(PurchaseOrder::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        // Order ids come from PurchaseOrderItemQueryService, which already checks source order access.
        return purchaseOrderRepository.findByIdInAndDeletedFlagFalse(orderIds).stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, order -> order));
    }

    private void refreshPurchaseOrderTotals(PurchaseOrder purchaseOrder) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            totalWeight = totalWeight.add(TradeItemCalculator.safeBigDecimal(item.getWeightTon()));
            totalAmount = totalAmount.add(TradeItemCalculator.safeBigDecimal(item.getAmount()));
        }
        purchaseOrder.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        purchaseOrder.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    record SourceWeighAccumulator(Integer quantity, BigDecimal weightTon) {
        boolean hasQuantity() {
            return quantity != null && quantity > 0;
        }

        BigDecimal averagePieceWeightTon() {
            return TradeItemCalculator.calculateAveragePieceWeightTon(quantity, weightTon);
        }

        boolean isFullyAllocated(Integer sourceQuantity) {
            return sourceQuantity != null && quantity != null && quantity >= sourceQuantity;
        }
    }
}
