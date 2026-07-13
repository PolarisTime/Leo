package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundCompletionSyncService {

    private static final BigDecimal FULFILLMENT_TOLERANCE = new BigDecimal("0.05");

    private final PurchaseInboundRepository repository;
    private final PurchaseInboundSourceValidator sourceValidator;
    private final PurchaseInboundAllocationService allocationService;

    public PurchaseInboundCompletionSyncService(PurchaseInboundRepository repository,
                                                PurchaseInboundSourceValidator sourceValidator,
                                                PurchaseInboundAllocationService allocationService) {
        this.repository = repository;
        this.sourceValidator = sourceValidator;
        this.allocationService = allocationService;
    }

    boolean shouldCompleteInbound(PurchaseInbound inbound) {
        if (!StatusConstants.AUDITED.equals(inbound.getStatus())) {
            return false;
        }
        return isFullyAllocated(inbound);
    }

    public void synchronizeAfterPurchaseRefundStatusChange(Collection<Long> sourcePurchaseOrderItemIds) {
        List<Long> affectedSourceItemIds = distinctSourceItemIds(sourcePurchaseOrderItemIds);
        if (affectedSourceItemIds.isEmpty()) {
            return;
        }
        List<PurchaseInbound> changedInbounds = new ArrayList<>();
        for (PurchaseInbound inbound : repository.findAllActiveBySourcePurchaseOrderItemIds(affectedSourceItemIds)) {
            if (!StatusConstants.AUDITED.equals(inbound.getStatus())
                    && !StatusConstants.INBOUND_COMPLETED.equals(inbound.getStatus())) {
                continue;
            }
            String nextStatus = isFullyAllocated(inbound)
                    ? StatusConstants.INBOUND_COMPLETED
                    : StatusConstants.AUDITED;
            if (!nextStatus.equals(inbound.getStatus())) {
                inbound.setStatus(nextStatus);
                changedInbounds.add(inbound);
            }
        }
        if (!changedInbounds.isEmpty()) {
            repository.saveAll(changedInbounds);
        }
    }

    private boolean isFullyAllocated(PurchaseInbound inbound) {
        List<Long> sourcePurchaseOrderItemIds = sourcePurchaseOrderItemIds(inbound);
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return false;
        }
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap =
                sourceValidator.loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        if (sourcePurchaseOrderItemMap.isEmpty()) {
            return false;
        }
        Map<Long, Integer> allocatedQuantityMap = allocationService.loadAllocatedQuantityMap(
                sourcePurchaseOrderItemIds,
                inbound.getId()
        );
        Map<Long, Integer> currentInboundQuantityMap = inbound.getItems().stream()
                .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                .collect(Collectors.groupingBy(
                        PurchaseInboundItem::getSourcePurchaseOrderItemId,
                        Collectors.summingInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                ));
        return sourcePurchaseOrderItemIds.stream().allMatch(sourceItemId -> {
            PurchaseOrderItem sourceItem = sourcePurchaseOrderItemMap.get(sourceItemId);
            if (sourceItem == null) {
                return false;
            }
            int expected = sourceItem.getQuantity() != null ? sourceItem.getQuantity() : 0;
            int actual = allocatedQuantityMap.getOrDefault(sourceItemId, 0)
                    + currentInboundQuantityMap.getOrDefault(sourceItemId, 0);
            return quantityWithinTolerance(expected, actual);
        });
    }

    void synchronizeSourcePurchaseOrders(PurchaseInbound inbound) {
        List<Long> sourcePurchaseOrderItemIds = sourcePurchaseOrderItemIds(inbound);
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return;
        }
        sourceValidator.loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds).values().stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(order -> order != null)
                .distinct()
                .forEach(this::maybeCompletePurchaseOrder);
    }

    private List<Long> sourcePurchaseOrderItemIds(PurchaseInbound inbound) {
        return inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private List<Long> distinctSourceItemIds(Collection<Long> sourcePurchaseOrderItemIds) {
        if (sourcePurchaseOrderItemIds == null) {
            return List.of();
        }
        return sourcePurchaseOrderItemIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private void maybeCompletePurchaseOrder(PurchaseOrder purchaseOrder) {
        if (!StatusConstants.AUDITED.equals(purchaseOrder.getStatus())
                && !StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
            return;
        }
        List<Long> sourceItemIds = purchaseOrder.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (sourceItemIds.isEmpty()) {
            return;
        }
        List<PurchaseInbound> allInbounds = repository
                .findAllActiveBySourcePurchaseOrderItemIds(sourceItemIds);
        boolean allInboundCompleted = allInbounds.stream()
                .allMatch(i -> StatusConstants.INBOUND_COMPLETED.equals(i.getStatus()));
        Map<Long, Integer> receivedQtyByItemId = allInbounds.stream()
                .flatMap(inbound -> inbound.getItems().stream())
                .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                .collect(Collectors.groupingBy(
                        PurchaseInboundItem::getSourcePurchaseOrderItemId,
                        Collectors.summingInt(
                                item -> item.getQuantity() != null ? item.getQuantity() : 0
                        )
                ));

        boolean allFulfilled = purchaseOrder.getItems().stream().allMatch(item -> {
            int expected = item.getQuantity() != null ? item.getQuantity() : 0;
            int actual = receivedQtyByItemId.getOrDefault(item.getId(), 0);
            return quantityWithinTolerance(expected, actual);
        });

        purchaseOrder.setStatus(allInboundCompleted && allFulfilled
                ? StatusConstants.PURCHASE_COMPLETED
                : StatusConstants.AUDITED);
    }

    private boolean quantityWithinTolerance(int expected, int actual) {
        if (expected == 0) {
            return actual == 0;
        }
        BigDecimal ratio = BigDecimal.valueOf(actual)
                .divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
        BigDecimal lowerBound = BigDecimal.ONE.subtract(FULFILLMENT_TOLERANCE);
        BigDecimal upperBound = BigDecimal.ONE.add(FULFILLMENT_TOLERANCE);
        return ratio.compareTo(lowerBound) >= 0 && ratio.compareTo(upperBound) <= 0;
    }
}
