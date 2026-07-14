package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.support.InvoiceAllocationSupport.AllocationProgress;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceReceiptCapacityService {

    private static final List<String> EFFECTIVE_INBOUND_STATUSES = List.of(
            StatusConstants.AUDITED,
            StatusConstants.INBOUND_COMPLETED
    );

    private final PurchaseInboundItemRepository purchaseInboundItemRepository;

    public InvoiceReceiptCapacityService(PurchaseInboundItemRepository purchaseInboundItemRepository) {
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
    }

    Map<Long, AllocationProgress> resolveCapacities(Collection<PurchaseOrderItem> sourceItems) {
        if (sourceItems == null || sourceItems.isEmpty()) {
            return Map.of();
        }
        List<Long> completedSourceItemIds = sourceItems.stream()
                .filter(this::isCompletedPurchase)
                .map(PurchaseOrderItem::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, AllocationProgress> completedCapacities = loadCompletedPurchaseCapacities(completedSourceItemIds);
        Map<Long, AllocationProgress> capacities = new HashMap<>();
        for (PurchaseOrderItem sourceItem : sourceItems) {
            if (sourceItem == null || sourceItem.getId() == null) {
                continue;
            }
            AllocationProgress capacity = isCompletedPurchase(sourceItem)
                    ? completedCapacities.getOrDefault(sourceItem.getId(), AllocationProgress.EMPTY)
                    : orderCapacity(sourceItem);
            capacities.put(sourceItem.getId(), capacity);
        }
        return Map.copyOf(capacities);
    }

    private Map<Long, AllocationProgress> loadCompletedPurchaseCapacities(List<Long> sourceItemIds) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AllocationProgress> capacities = new HashMap<>();
        for (PurchaseInboundItemRepository.PurchaseOrderInvoiceCapacitySummary summary
                : purchaseInboundItemRepository.summarizeInvoiceCapacityBySourcePurchaseOrderItemIds(
                        sourceItemIds,
                        EFFECTIVE_INBOUND_STATUSES
                )) {
            capacities.put(
                    summary.getSourcePurchaseOrderItemId(),
                    new AllocationProgress(
                            summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity(),
                            TradeItemCalculator.safeBigDecimal(summary.getTotalWeightTon()),
                            TradeItemCalculator.safeBigDecimal(summary.getTotalAmount())
                    )
            );
        }
        return capacities;
    }

    private AllocationProgress orderCapacity(PurchaseOrderItem sourceItem) {
        return new AllocationProgress(
                sourceItem.getQuantity() == null ? 0L : sourceItem.getQuantity().longValue(),
                TradeItemCalculator.safeBigDecimal(sourceItem.getWeightTon()),
                TradeItemCalculator.safeBigDecimal(sourceItem.getAmount())
        );
    }

    private boolean isCompletedPurchase(PurchaseOrderItem sourceItem) {
        return sourceItem != null
                && sourceItem.getPurchaseOrder() != null
                && StatusConstants.PURCHASE_COMPLETED.equals(sourceItem.getPurchaseOrder().getStatus());
    }
}
