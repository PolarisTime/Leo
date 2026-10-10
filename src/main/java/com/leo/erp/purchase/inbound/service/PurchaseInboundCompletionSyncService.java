package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseSupplierLedgerLock;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.audit.PurchaseOrderAuditPublisher;
import com.leo.erp.purchase.order.service.PurchaseOrderDirectSalesCapacityGuard;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundCompletionSyncService {

    private final PurchaseInboundSourceValidator sourceValidator;
    private final PurchaseInboundAllocationService allocationService;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final PurchaseSupplierLedgerLock supplierLedgerLock;
    private final PurchaseOrderDirectSalesCapacityGuard directSalesCapacityGuard;
    private final PurchaseOrderAuditPublisher purchaseOrderAuditPublisher;

    public PurchaseInboundCompletionSyncService(PurchaseInboundSourceValidator sourceValidator,
                                                PurchaseInboundAllocationService allocationService,
                                                PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                                PurchaseSupplierLedgerLock supplierLedgerLock,
                                                PurchaseOrderDirectSalesCapacityGuard directSalesCapacityGuard,
                                                PurchaseOrderAuditPublisher purchaseOrderAuditPublisher) {
        this.sourceValidator = sourceValidator;
        this.allocationService = allocationService;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.supplierLedgerLock = supplierLedgerLock;
        this.directSalesCapacityGuard = directSalesCapacityGuard;
        this.purchaseOrderAuditPublisher = purchaseOrderAuditPublisher;
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
        // 强制结单单据由人工终结: 剩余量已作废, 入库审核/反审核都不得改写其状态。
        if (purchaseOrder.isForceClosed()) {
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
        // 完成采购判定按累计有效入库件数: 未删除且状态为已审核/完成入库的入库单;
        // 草稿不参与, 历史部分入库单无需回填为完成入库即可计入。
        Map<Long, Integer> receivedQtyByItemId =
                directSalesCapacityGuard.loadReceivedQuantityMap(sourceItemIds);

        boolean allFulfilled = purchaseOrder.getItems().stream().allMatch(item -> {
            int expected = item.getQuantity() != null ? item.getQuantity() : 0;
            int actual = receivedQtyByItemId.getOrDefault(item.getId(), 0);
            return expected >= 1 && expected == actual;
        });

        if (allFulfilled) {
            directSalesCapacityGuard.assertCovered(sourceItemIds, receivedQtyByItemId);
            if (!StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
                lockSupplierLedger(purchaseOrder);
                purchaseOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
                publishStatusEvent(
                        purchaseOrder,
                        "PURCHASE_ORDER_COMPLETED",
                        StatusConstants.PURCHASE_COMPLETED,
                        "采购订单状态 已审核 -> 完成采购"
                );
            }
        } else if (allowReopen && StatusConstants.PURCHASE_COMPLETED.equals(purchaseOrder.getStatus())) {
            purchaseOrder.setStatus(StatusConstants.AUDITED);
            publishStatusEvent(
                    purchaseOrder,
                    "PURCHASE_ORDER_REOPENED",
                    "退回已审核",
                    "采购订单状态 完成采购 -> 已审核"
            );
        }
    }

    private void publishStatusEvent(PurchaseOrder purchaseOrder,
                                    String eventType,
                                    String actionType,
                                    String remark) {
        purchaseOrderAuditPublisher.publish(purchaseOrder, eventType, actionType, remark);
    }

    private void lockSupplierLedger(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getSettlementCompanyId() == null || purchaseOrder.getSupplierId() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单缺少供应商或结算主体身份，不能完成采购"
            );
        }
        supplierLedgerLock.lock(
                purchaseOrder.getSettlementCompanyId(),
                purchaseOrder.getSupplierId()
        );
    }

}
