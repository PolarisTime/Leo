package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.refund.repository.PurchaseRefundRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PurchaseInboundRefundGuard {

    private final PurchaseRefundRepository purchaseRefundRepository;

    public PurchaseInboundRefundGuard(PurchaseRefundRepository purchaseRefundRepository) {
        this.purchaseRefundRepository = purchaseRefundRepository;
    }

    void assertStatusTransitionAllowed(PurchaseInbound inbound,
                                       String currentStatus,
                                       String nextStatus) {
        if (!crossesAuditBoundary(currentStatus, nextStatus)) {
            return;
        }
        List<Long> sourceItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();
        if (sourceItemIds.isEmpty()
                || purchaseRefundRepository
                .summarizeAuditedQuantityBySourcePurchaseOrderItemIds(sourceItemIds)
                .isEmpty()) {
            return;
        }
        String action = StatusConstants.AUDITED.equals(nextStatus) ? "审核" : "反审核";
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "来源采购订单已存在已审核采购退款单，不能" + action + "采购入库，请先反审核采购退款单"
        );
    }

    private boolean crossesAuditBoundary(String currentStatus, String nextStatus) {
        return (StatusConstants.DRAFT.equals(currentStatus) && StatusConstants.AUDITED.equals(nextStatus))
                || (StatusConstants.AUDITED.equals(currentStatus) && StatusConstants.DRAFT.equals(nextStatus));
    }
}
