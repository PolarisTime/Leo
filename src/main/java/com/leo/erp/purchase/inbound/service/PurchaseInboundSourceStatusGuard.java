package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PurchaseInboundSourceStatusGuard {

    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public PurchaseInboundSourceStatusGuard(
            PurchaseOrderItemQueryService purchaseOrderItemQueryService,
            PurchaseInboundItemRepository purchaseInboundItemRepository,
            SalesOrderItemRepository salesOrderItemRepository,
            SourceAllocationLockService sourceAllocationLockService
    ) {
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    void assertStatusTransitionAllowed(PurchaseInbound inbound,
                                       String currentStatus,
                                       String nextStatus) {
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            assertSourcePurchaseOrderNotCompleted(inbound);
        }
        if (StatusConstants.DRAFT.equals(nextStatus)
                && (StatusConstants.AUDITED.equals(currentStatus)
                || StatusConstants.INBOUND_COMPLETED.equals(currentStatus))) {
            assertNoActiveSalesOrderReferences(inbound, "反审核");
        }
    }

    void assertDeletionAllowed(PurchaseInbound inbound) {
        assertNoActiveSalesOrderReferences(inbound, "删除");
    }

    private void assertSourcePurchaseOrderNotCompleted(PurchaseInbound inbound) {
        List<Long> sourceItemIds = sourceItemIds(inbound);
        if (sourceItemIds.isEmpty()) {
            return;
        }
        boolean sourcePurchaseCompleted = purchaseOrderItemQueryService.findActiveByIdIn(sourceItemIds).stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(Objects::nonNull)
                .anyMatch(order -> StatusConstants.PURCHASE_COMPLETED.equals(order.getStatus()));
        if (sourcePurchaseCompleted) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源采购订单已完成采购，不能变更采购入库状态，请先撤销完成采购"
            );
        }
    }

    private void assertNoActiveSalesOrderReferences(PurchaseInbound inbound, String action) {
        List<Long> currentSourceItemIds = sourceItemIds(inbound);
        if (currentSourceItemIds.isEmpty()) {
            return;
        }
        List<Long> sourceItemIds = purchaseOrderItemQueryService.findActiveByIdIn(currentSourceItemIds).stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(Objects::nonNull)
                .flatMap(order -> order.getItems().stream())
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<Long> inboundItemIds = purchaseInboundItemRepository
                .findAllActiveBySourcePurchaseOrderItemIds(sourceItemIds).stream()
                .map(PurchaseInboundItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        sourceAllocationLockService.lockTradeItemSources(sourceItemIds, inboundItemIds, List.of());
        boolean referencedByPurchaseOrder = !sourceItemIds.isEmpty()
                && !salesOrderItemRepository.findActiveBySourcePurchaseOrderItemIds(sourceItemIds).isEmpty();
        boolean referencedByInbound = !inboundItemIds.isEmpty()
                && !salesOrderItemRepository.findActiveBySourceInboundItemIds(inboundItemIds).isEmpty();
        if (referencedByPurchaseOrder || referencedByInbound) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源采购订单仍被销售订单引用，不能" + action + "采购入库，请先删除相关销售订单"
            );
        }
    }

    private List<Long> sourceItemIds(PurchaseInbound inbound) {
        return inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
