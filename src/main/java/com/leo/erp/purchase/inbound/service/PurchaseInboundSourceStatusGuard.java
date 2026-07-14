package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PurchaseInboundSourceStatusGuard {

    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;

    public PurchaseInboundSourceStatusGuard(PurchaseOrderItemQueryService purchaseOrderItemQueryService) {
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
    }

    void assertStatusTransitionAllowed(PurchaseInbound inbound,
                                       String currentStatus,
                                       String nextStatus) {
        if (!crossesAuditBoundary(currentStatus, nextStatus)) {
            return;
        }
        List<Long> sourceItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
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

    private boolean crossesAuditBoundary(String currentStatus, String nextStatus) {
        return (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus))
                || (StatusConstants.DRAFT.equals(nextStatus)
                && (StatusConstants.AUDITED.equals(currentStatus)
                || StatusConstants.INBOUND_COMPLETED.equals(currentStatus)));
    }
}
