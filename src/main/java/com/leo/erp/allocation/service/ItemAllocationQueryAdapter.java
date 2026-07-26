package com.leo.erp.allocation.service;

import com.leo.erp.allocation.api.ItemAllocationQuery;
import com.leo.erp.allocation.api.ItemAllocationSummary;
import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/** 将 allocation 内部 JDBC 投影适配为稳定的模块公开契约。 */
@Service
public class ItemAllocationQueryAdapter implements ItemAllocationQuery {

    private final ItemAllocationNativeRepository itemAllocationRepository;

    public ItemAllocationQueryAdapter(ItemAllocationNativeRepository itemAllocationRepository) {
        this.itemAllocationRepository = itemAllocationRepository;
    }

    @Override
    public List<ItemAllocationSummary> summarizeSalesByPurchaseOrderItemIds(
            Collection<Long> purchaseOrderItemIds
    ) {
        return itemAllocationRepository.summarizeSalesByPurchaseOrderItems(purchaseOrderItemIds, null).stream()
                .map(ItemAllocationQueryAdapter::toSummary)
                .toList();
    }

    @Override
    public List<ItemAllocationSummary> summarizeSalesByInboundItemIds(Collection<Long> inboundItemIds) {
        return itemAllocationRepository.summarizeSalesByInboundItems(inboundItemIds, null).stream()
                .map(ItemAllocationQueryAdapter::toSummary)
                .toList();
    }

    private static ItemAllocationSummary toSummary(
            ItemAllocationNativeRepository.AllocationProjection projection
    ) {
        return new ItemAllocationSummary(
                projection.getSourceItemId(),
                projection.getTotalQuantity()
        );
    }
}
