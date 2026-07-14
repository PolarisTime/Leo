package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundCompletionSyncService {

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
            return expected == actual;
        });
    }

    void synchronizeSourcePurchaseOrders(PurchaseInbound inbound, boolean allowReopen) {
        List<Long> sourcePurchaseOrderItemIds = sourcePurchaseOrderItemIds(inbound);
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return;
        }
        sourceValidator.loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds).values().stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(order -> order != null)
                .distinct()
                .forEach(order -> synchronizePurchaseOrder(order, allowReopen));
    }

    private List<Long> sourcePurchaseOrderItemIds(PurchaseInbound inbound) {
        return inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private void synchronizePurchaseOrder(PurchaseOrder purchaseOrder, boolean allowReopen) {
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
            return expected >= 1 && expected == actual;
        });

        if (allInboundCompleted && allFulfilled) {
            purchaseOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
        } else if (allowReopen && StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
            purchaseOrder.setStatus(StatusConstants.AUDITED);
        }
    }

}
