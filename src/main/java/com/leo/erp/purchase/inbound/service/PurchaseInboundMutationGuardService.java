package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 采购入库变更守卫：来源采购订单行锁定、保存状态不变校验、
 * 审核前置行校验与状态流转/删除守卫。
 */
@Service
public class PurchaseInboundMutationGuardService {

    private final SourceAllocationLockService sourceAllocationLockService;
    private final PurchaseInboundSourceStatusGuard purchaseInboundSourceStatusGuard;

    public PurchaseInboundMutationGuardService(SourceAllocationLockService sourceAllocationLockService,
                                               PurchaseInboundSourceStatusGuard purchaseInboundSourceStatusGuard) {
        this.sourceAllocationLockService = sourceAllocationLockService;
        this.purchaseInboundSourceStatusGuard = purchaseInboundSourceStatusGuard;
    }

    /**
     * 编辑保存删行前的下游守卫（审计 #4）：被移除的入库明细若仍被活跃销售订单引用，
     * 直接删行会撞 RESTRICT 外键变成 409；这里改为明确的业务提示。
     *
     * <p>必须在 {@link #lockSourcePurchaseOrderItems} 之前调用：本方法先锁采购入库明细
     * （rank 20/21），采购订单明细锁（rank 30/31）随后才获取，保持全局加锁顺序一致。</p>
     */
    void assertRemovedLineItemsNotReferenced(PurchaseInbound inbound, PurchaseInboundRequest request) {
        List<Long> removedItemIds = removedItemIds(inbound, request);
        if (removedItemIds.isEmpty()) {
            return;
        }
        purchaseInboundSourceStatusGuard.assertRemovedItemsNotReferencedByActiveSalesOrders(removedItemIds);
    }

    /** 本次保存中被移除的入库明细 ID：库里存在、请求里不再出现（新增行 id 为空，不参与比较）。 */
    private List<Long> removedItemIds(PurchaseInbound inbound, PurchaseInboundRequest request) {
        if (inbound == null || inbound.getItems() == null || inbound.getItems().isEmpty()) {
            return List.of();
        }
        Set<Long> requestedIds = new HashSet<>();
        if (request != null && request.items() != null) {
            for (PurchaseInboundItemRequest item : request.items()) {
                if (item != null && item.id() != null) {
                    requestedIds.add(item.id());
                }
            }
        }
        return inbound.getItems().stream()
                .map(PurchaseInboundItem::getId)
                .filter(Objects::nonNull)
                .filter(id -> !requestedIds.contains(id))
                .distinct()
                .sorted()
                .toList();
    }

    void lockSourcePurchaseOrderItems(PurchaseInbound inbound, PurchaseInboundRequest request) {
        Stream<Long> existingSourceIds = inbound == null
                ? Stream.empty()
                : inbound.getItems().stream().map(PurchaseInboundItem::getSourcePurchaseOrderItemId);
        Stream<Long> requestedSourceIds = request == null
                ? Stream.empty()
                : request.items().stream().map(PurchaseInboundItemRequest::sourcePurchaseOrderItemId);
        List<Long> sourceIds = Stream.concat(existingSourceIds, requestedSourceIds)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        sourceAllocationLockService.lockTradeItemSources(sourceIds, List.of(), List.of());
    }

    void assertSaveDoesNotChangeStatus(PurchaseInbound inbound, String requestedStatus) {
        String currentStatus = inbound.getStatus();
        if (currentStatus == null) {
            if (!StatusConstants.DRAFT.equals(requestedStatus)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "新建采购入库只能保存为草稿，审核请使用审核命令"
                );
            }
            return;
        }
        if (!currentStatus.equals(requestedStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "普通保存不能修改采购入库状态，请使用审核或反审核命令"
            );
        }
    }

    void prepareStatusTransition(PurchaseInbound inbound, String currentStatus, String nextStatus) {
        if (PurchaseInboundSourceStatusGuard.isReverseStatusTransition(currentStatus, nextStatus)) {
            purchaseInboundSourceStatusGuard.lockReverseReferenceSources(inbound);
        }
        lockSourcePurchaseOrderItems(inbound, null);
        if (!StatusConstants.DRAFT.equals(nextStatus)) {
            assertAuditableLineItems(inbound);
        }
        purchaseInboundSourceStatusGuard.assertStatusTransitionAllowed(inbound, currentStatus, nextStatus);
    }

    void assertDeletionAllowed(PurchaseInbound inbound) {
        purchaseInboundSourceStatusGuard.lockReverseReferenceSources(inbound);
        lockSourcePurchaseOrderItems(inbound, null);
        purchaseInboundSourceStatusGuard.assertDeletionAllowed(inbound);
    }

    private void assertAuditableLineItems(PurchaseInbound inbound) {
        for (PurchaseInboundItem item : inbound.getItems()) {
            int lineNo = item.getLineNo() == null ? 0 : item.getLineNo();
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + lineNo + "行入库数量必须大于0"
                );
            }
            if ("过磅".equals(item.getSettlementMode())
                    && (item.getWeighWeightTon() == null
                    || item.getWeighWeightTon().signum() <= 0)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + lineNo + "行需填写大于0的过磅重量后才能审核"
                );
            }
        }
    }
}
