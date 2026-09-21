package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.api.PurchaseOrderReferenceGuard;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundSourceStatusGuard {

    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final PurchaseOrderReferenceGuard purchaseOrderReferenceGuard;
    private final SourceAllocationLockService sourceAllocationLockService;
    private final PurchaseInboundAllocationService allocationService;

    public PurchaseInboundSourceStatusGuard(
            PurchaseOrderItemQueryService purchaseOrderItemQueryService,
            PurchaseInboundItemRepository purchaseInboundItemRepository,
            PurchaseOrderReferenceGuard purchaseOrderReferenceGuard,
            SourceAllocationLockService sourceAllocationLockService,
            PurchaseInboundAllocationService allocationService
    ) {
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderReferenceGuard = purchaseOrderReferenceGuard;
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.allocationService = allocationService;
    }

    void assertStatusTransitionAllowed(PurchaseInbound inbound,
                                       String currentStatus,
                                       String nextStatus) {
        if (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus)) {
            assertSourcePurchaseOrderNotCompleted(inbound);
            assertSourcePurchaseOrderFullyAllocated(inbound);
        }
        if (isReverseStatusTransition(currentStatus, nextStatus)) {
            assertNoActiveSalesOrderReferences(inbound, "反审核");
        }
    }

    static boolean isReverseStatusTransition(String currentStatus, String nextStatus) {
        return StatusConstants.DRAFT.equals(nextStatus)
                && (StatusConstants.AUDITED.equals(currentStatus)
                || StatusConstants.INBOUND_COMPLETED.equals(currentStatus));
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
                    "来源采购订单已完成采购，必须先反审核原采购入库后才能重新审核"
            );
        }
    }

    private void assertSourcePurchaseOrderFullyAllocated(PurchaseInbound inbound) {
        List<Long> inboundSourceItemIds = sourceItemIds(inbound);
        if (inboundSourceItemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库缺少来源采购订单明细");
        }
        List<PurchaseOrderItem> inboundSourceItems =
                purchaseOrderItemQueryService.findActiveByIdIn(inboundSourceItemIds);
        if (inboundSourceItems.size() != inboundSourceItemIds.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库的来源采购订单明细已失效");
        }
        Set<Long> sourceOrderIds = inboundSourceItems.stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(Objects::nonNull)
                .map(PurchaseOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sourceOrderIds.size() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "一张采购入库单必须且只能关联一张采购订单");
        }

        PurchaseOrder sourceOrder = inboundSourceItems.getFirst().getPurchaseOrder();
        List<PurchaseOrderItem> sourceOrderItems = sourceOrder.getItems();
        List<Long> sourceOrderItemIds = sourceOrderItems.stream()
                .map(PurchaseOrderItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sourceOrderItemIds.isEmpty() || sourceOrderItemIds.size() != sourceOrderItems.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单存在无效商品明细，不能审核采购入库");
        }

        Map<Long, Integer> allocatedQuantityMap =
                allocationService.loadAllocatedQuantityMap(sourceOrderItemIds, null);
        boolean fullyAllocated = sourceOrderItems.stream().allMatch(item -> {
            int orderedQuantity = item.getQuantity() == null ? 0 : item.getQuantity();
            int allocatedQuantity = allocatedQuantityMap.getOrDefault(item.getId(), 0);
            return orderedQuantity >= 1 && allocatedQuantity == orderedQuantity;
        });
        if (!fullyAllocated) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "采购订单必须全部商品一次性完成入库，不允许分批审核"
            );
        }
    }

    /**
     * 预锁反审核/删除需要检查的全部来源行（采购订单明细与采购入库明细）。
     *
     * <p>入库锁 rank 20/21 低于采购订单 30/31，因此必须在调用方持有采购订单锁之前调用，
     * 由本方法一次性按全局顺序获取，避免与「先锁入库、再锁采购订单」的事务形成 AB-BA 死锁。
     */
    void lockReverseReferenceSources(PurchaseInbound inbound) {
        ReverseReferenceSources references = resolveReverseReferenceSources(inbound);
        sourceAllocationLockService.lockTradeItemSources(
                references.sourceItemIds(), references.inboundItemIds(), List.of());
    }

    private void assertNoActiveSalesOrderReferences(PurchaseInbound inbound, String action) {
        ReverseReferenceSources references = resolveReverseReferenceSources(inbound);
        if (references.sourceItemIds().isEmpty()) {
            return;
        }
        sourceAllocationLockService.lockTradeItemSources(
                references.sourceItemIds(), references.inboundItemIds(), List.of());
        boolean referencedByPurchaseOrder =
                purchaseOrderReferenceGuard.hasActivePurchaseOrderItemReferences(references.sourceItemIds());
        boolean referencedByInbound = !references.inboundItemIds().isEmpty()
                && purchaseOrderReferenceGuard.hasActiveInboundItemReferences(references.inboundItemIds());
        if (referencedByPurchaseOrder || referencedByInbound) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源采购订单仍被销售订单引用，不能" + action + "采购入库，请先删除相关销售订单"
            );
        }
    }

    private ReverseReferenceSources resolveReverseReferenceSources(PurchaseInbound inbound) {
        List<Long> currentSourceItemIds = sourceItemIds(inbound);
        if (currentSourceItemIds.isEmpty()) {
            return new ReverseReferenceSources(List.of(), List.of());
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
        return new ReverseReferenceSources(sourceItemIds, inboundItemIds);
    }

    private record ReverseReferenceSources(List<Long> sourceItemIds, List<Long> inboundItemIds) {
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
