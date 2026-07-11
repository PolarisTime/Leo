package com.leo.erp.purchase.refund.mapper;

import com.leo.erp.purchase.refund.domain.entity.PurchaseRefund;
import com.leo.erp.purchase.refund.domain.entity.PurchaseRefundItem;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundItemResponse;
import com.leo.erp.purchase.refund.web.dto.PurchaseRefundResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PurchaseRefundMapper {

    public PurchaseRefundResponse toSummaryResponse(PurchaseRefund entity) {
        return toResponse(entity, List.of());
    }

    public PurchaseRefundResponse toDetailResponse(PurchaseRefund entity) {
        List<PurchaseRefundItemResponse> items = entity.getItems() == null
                ? List.of()
                : entity.getItems().stream()
                .sorted(Comparator.comparing(PurchaseRefundItem::getLineNo))
                .map(this::toItemResponse)
                .toList();
        return toResponse(entity, items);
    }

    private PurchaseRefundResponse toResponse(PurchaseRefund entity,
                                              List<PurchaseRefundItemResponse> items) {
        return new PurchaseRefundResponse(
                entity.getId(),
                entity.getRefundNo(),
                entity.getSourcePurchaseOrderId(),
                entity.getPurchaseOrderNo(),
                entity.getSupplierCode(),
                entity.getSupplierName(),
                entity.getSettlementCompanyId(),
                entity.getSettlementCompanyName(),
                entity.getRefundDate(),
                entity.getTotalQuantity(),
                entity.getTotalWeight(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.isDeletedFlag(),
                entity.getOperatorName(),
                entity.getRemark(),
                items
        );
    }

    private PurchaseRefundItemResponse toItemResponse(PurchaseRefundItem item) {
        return new PurchaseRefundItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourcePurchaseOrderItemId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
