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

import java.util.List;
import java.util.Objects;
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
        lockSourcePurchaseOrderItems(inbound, null);
        if (!StatusConstants.DRAFT.equals(nextStatus)) {
            assertAuditableLineItems(inbound);
        }
        purchaseInboundSourceStatusGuard.assertStatusTransitionAllowed(inbound, currentStatus, nextStatus);
    }

    void assertDeletionAllowed(PurchaseInbound inbound) {
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
