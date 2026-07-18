package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PurchaseInboundWeightWriteBackService {

    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseInboundSourceValidator sourceValidator;

    public PurchaseInboundWeightWriteBackService(PurchaseInboundItemRepository purchaseInboundItemRepository,
                                                 PurchaseOrderRepository purchaseOrderRepository,
                                                 PurchaseInboundSourceValidator sourceValidator) {
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.sourceValidator = sourceValidator;
    }

    void synchronizeAfterSave(PurchaseInbound inbound) {
        List<Long> sourceItemIds = Stream.concat(
                        inbound.getItems().stream().map(PurchaseInboundItem::getSourcePurchaseOrderItemId),
                        inbound.getAffectedSourcePurchaseOrderItemIds().stream()
                )
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (sourceItemIds.isEmpty() || sourceValidator == null) {
            return;
        }
        writeBackPurchaseOrderWeights(
                sourceItemIds,
                inbound.getId(),
                isEffective(inbound) ? currentEffectiveWeightAccumulatorMap(inbound) : Map.of(),
                sourceValidator.loadSourcePurchaseOrderItemMap(sourceItemIds)
        );
    }

    void writeBackPurchaseOrderWeights(
            List<Long> sourcePurchaseOrderItemIds,
            Long currentInboundId,
            Map<Long, SourceWeighAccumulator> currentWeightAccumulatorMap,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap
    ) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return;
        }
        Map<Long, SourceWeighAccumulator> persistedWeightAccumulatorMap = loadPersistedWeightAccumulatorMap(
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
                    persistedWeightAccumulatorMap.get(sourcePurchaseOrderItemId),
                    currentWeightAccumulatorMap.get(sourcePurchaseOrderItemId)
            );
            if (weighAccumulator != null && weighAccumulator.hasQuantity()) {
                BigDecimal actualWeightTon = TradeItemCalculator.scaleWeightTon(weighAccumulator.weightTon());
                sourceItem.setActualWeightTon(actualWeightTon);
            } else {
                sourceItem.setActualWeightTon(null);
            }
        }
        affectedOrderMap.values().forEach(this::refreshPurchaseOrderTotals);
        if (!affectedOrderMap.isEmpty()) {
            purchaseOrderRepository.saveAll(affectedOrderMap.values());
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

    private Map<Long, SourceWeighAccumulator> loadPersistedWeightAccumulatorMap(
            List<Long> sourcePurchaseOrderItemIds, Long currentInboundId
    ) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SourceWeighAccumulator> weighAccumulatorMap = new HashMap<>();
        purchaseInboundItemRepository.summarizeEffectiveWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                        sourcePurchaseOrderItemIds,
                        currentInboundId,
                        List.of(StatusConstants.AUDITED, StatusConstants.INBOUND_COMPLETED)
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

    private boolean isEffective(PurchaseInbound inbound) {
        return !inbound.isDeletedFlag()
                && (StatusConstants.AUDITED.equals(inbound.getStatus())
                || StatusConstants.INBOUND_COMPLETED.equals(inbound.getStatus()));
    }

    private Map<Long, SourceWeighAccumulator> currentEffectiveWeightAccumulatorMap(PurchaseInbound inbound) {
        Map<Long, SourceWeighAccumulator> accumulatorMap = new HashMap<>();
        inbound.getItems().stream()
                .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                .forEach(item -> accumulatorMap.merge(
                        item.getSourcePurchaseOrderItemId(),
                        new SourceWeighAccumulator(
                                item.getQuantity(),
                                TradeItemCalculator.scaleWeightTon(
                                        item.getWeighWeightTon() != null
                                                ? item.getWeighWeightTon()
                                                : item.getWeightTon()
                                )
                        ),
                        this::mergeWeighAccumulator
                ));
        return accumulatorMap;
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
    }
}
