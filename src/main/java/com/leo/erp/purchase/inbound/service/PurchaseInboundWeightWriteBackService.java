package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.purchase.order.service.PurchaseOrderPlanWeightSyncService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService;
    private final PurchaseOrderPlanWeightSyncService purchaseOrderPlanWeightSyncService;
    private final PurchaseInboundSourceValidator sourceValidator;

    public PurchaseInboundWeightWriteBackService(PurchaseInboundItemRepository purchaseInboundItemRepository,
                                                 PurchaseOrderRepository purchaseOrderRepository,
                                                 PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService) {
        this(purchaseInboundItemRepository, purchaseOrderRepository, purchaseOrderItemPieceWeightService, null, null);
    }

    @Autowired
    public PurchaseInboundWeightWriteBackService(PurchaseInboundItemRepository purchaseInboundItemRepository,
                                                 PurchaseOrderRepository purchaseOrderRepository,
                                                 PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService,
                                                 PurchaseOrderPlanWeightSyncService purchaseOrderPlanWeightSyncService,
                                                 PurchaseInboundSourceValidator sourceValidator) {
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemPieceWeightService = purchaseOrderItemPieceWeightService;
        this.purchaseOrderPlanWeightSyncService = purchaseOrderPlanWeightSyncService;
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
                isEffective(inbound) ? currentWeighAccumulatorMap(inbound) : Map.of(),
                sourceValidator.loadSourcePurchaseOrderItemMap(sourceItemIds)
        );
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
                BigDecimal actualWeightTon = TradeItemCalculator.scaleWeightTon(weighAccumulator.weightTon());
                sourceItem.setActualWeightTon(actualWeightTon);
                sourceItem.setActualPieceWeightTon(TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(
                        weighAccumulator.quantity(),
                        actualWeightTon
                ));
            } else {
                sourceItem.setActualWeightTon(null);
                sourceItem.setActualPieceWeightTon(null);
            }
        }
        affectedOrderMap.values().forEach(this::refreshPurchaseOrderTotals);
        if (!affectedOrderMap.isEmpty()) {
            purchaseOrderRepository.saveAll(affectedOrderMap.values());
            List<PurchaseOrderItem> writeBackItems = sourcePurchaseOrderItemIds.stream()
                    .map(writeBackItemMap::get)
                    .filter(item -> item != null)
                    .toList();
            syncPlanWeights(writeBackItems);
        }
    }

    private void syncPlanWeights(List<PurchaseOrderItem> writeBackItems) {
        if (purchaseOrderPlanWeightSyncService != null) {
            purchaseOrderPlanWeightSyncService.syncAfterPurchaseOrderWeightWriteBack(writeBackItems);
            return;
        }
        purchaseOrderItemPieceWeightService.regenerateForPurchaseOrderItems(writeBackItems);
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

    private Map<Long, SourceWeighAccumulator> currentWeighAccumulatorMap(PurchaseInbound inbound) {
        Map<Long, SourceWeighAccumulator> accumulatorMap = new HashMap<>();
        inbound.getItems().stream()
                .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                .filter(item -> item.getWeighWeightTon() != null)
                .forEach(item -> accumulatorMap.merge(
                        item.getSourcePurchaseOrderItemId(),
                        new SourceWeighAccumulator(
                                item.getQuantity(),
                                TradeItemCalculator.scaleWeightTon(item.getWeighWeightTon())
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
