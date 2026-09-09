package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;

final class PurchaseOrderSaveValidations {

    private PurchaseOrderSaveValidations() {
    }

    static void assertLineQuantities(PurchaseOrderRequest request) {
        for (int index = 0; index < request.items().size(); index++) {
            Integer quantity = request.items().get(index).quantity();
            if (quantity == null || quantity < 1) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "第" + (index + 1) + "行数量必须至少为1个数量单位"
                );
            }
        }
    }

    static void assertAuditableLineQuantities(PurchaseOrder purchaseOrder) {
        for (var item : purchaseOrder.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + item.getLineNo() + "行数量必须至少为1个数量单位"
                );
            }
        }
    }

    static void assertStatusNotChangedBySave(PurchaseOrder purchaseOrder, String requestedStatus) {
        String currentStatus = purchaseOrder.getStatus();
        if (currentStatus == null) {
            if (!StatusConstants.DRAFT.equals(requestedStatus)) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "新建采购订单只能保存为草稿，审核请使用审核命令"
                );
            }
            return;
        }
        if (!currentStatus.equals(requestedStatus)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "普通保存不能修改采购订单状态，请使用审核或反审核操作"
            );
        }
    }

    static void assertSettlementCompanyMutable(PurchaseOrder purchaseOrder, Long requestedSettlementCompanyId) {
        if (purchaseOrder.getId() == null || purchaseOrder.getSettlementCompanyId() == null) {
            return;
        }
        if (StatusConstants.AUDITED.equals(purchaseOrder.getStatus())
                && !purchaseOrder.getSettlementCompanyId().equals(requestedSettlementCompanyId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已审核采购订单不允许修改采购结算主体");
        }
    }
}
