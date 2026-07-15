package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.common.service.SupplierLedgerLockService;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundCompletionSyncService {

    private final PurchaseInboundRepository repository;
    private final PurchaseInboundSourceValidator sourceValidator;
    private final PurchaseInboundAllocationService allocationService;
    private final SupplierLedgerLockService supplierLedgerLockService;
    private final SalesOrderItemRepository salesOrderItemRepository;

    public PurchaseInboundCompletionSyncService(PurchaseInboundRepository repository,
                                                PurchaseInboundSourceValidator sourceValidator,
                                                PurchaseInboundAllocationService allocationService,
                                                SupplierLedgerLockService supplierLedgerLockService,
                                                SalesOrderItemRepository salesOrderItemRepository) {
        this.repository = repository;
        this.sourceValidator = sourceValidator;
        this.allocationService = allocationService;
        this.supplierLedgerLockService = supplierLedgerLockService;
        this.salesOrderItemRepository = salesOrderItemRepository;
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
            assertLegacyDirectSalesCapacityCovered(sourceItemIds, receivedQtyByItemId);
            if (!StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
                lockSupplierLedger(purchaseOrder);
                purchaseOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
            }
        } else if (allowReopen && StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
            purchaseOrder.setStatus(StatusConstants.AUDITED);
        }
    }

    private void assertLegacyDirectSalesCapacityCovered(
            List<Long> sourceItemIds,
            Map<Long, Integer> receivedQtyByItemId
    ) {
        for (SalesOrderItemRepository.SourcePurchaseOrderAllocationSummary summary
                : salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(
                sourceItemIds,
                null
        )) {
            long inboundQuantity = receivedQtyByItemId.getOrDefault(
                    summary.getSourcePurchaseOrderItemId(),
                    0
            );
            long directSalesQuantity = summary.getTotalQuantity() == null ? 0L : summary.getTotalQuantity();
            if (directSalesQuantity > inboundQuantity) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "来源采购明细 " + summary.getSourcePurchaseOrderItemId()
                                + " 的历史直连销售数量超过最终入库量：已入库 " + inboundQuantity
                                + " 件，已占用 " + directSalesQuantity + " 件，请先处理历史销售订单"
                );
            }
        }
    }

    private void lockSupplierLedger(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getSettlementCompanyId() == null || purchaseOrder.getSupplierId() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单缺少供应商或结算主体身份，不能完成采购"
            );
        }
        supplierLedgerLockService.lock(
                purchaseOrder.getSettlementCompanyId(),
                purchaseOrder.getSupplierId()
        );
    }

}
