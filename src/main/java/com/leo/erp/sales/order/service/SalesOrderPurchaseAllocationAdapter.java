package com.leo.erp.sales.order.service;

import com.leo.erp.purchase.api.PurchaseOrderSalesAllocation;
import com.leo.erp.purchase.api.PurchaseOrderSalesAllocationQuery;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class SalesOrderPurchaseAllocationAdapter implements PurchaseOrderSalesAllocationQuery {

    private final SalesOrderItemRepository salesOrderItemRepository;

    public SalesOrderPurchaseAllocationAdapter(SalesOrderItemRepository salesOrderItemRepository) {
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    @Override
    public List<PurchaseOrderSalesAllocation> summarizeByPurchaseOrderItemIds(
            Collection<Long> purchaseOrderItemIds
    ) {
        return salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(
                        purchaseOrderItemIds,
                        null
                ).stream()
                .map(summary -> new PurchaseOrderSalesAllocation(
                        summary.getSourcePurchaseOrderItemId(),
                        summary.getTotalQuantity()
                ))
                .toList();
    }
}
