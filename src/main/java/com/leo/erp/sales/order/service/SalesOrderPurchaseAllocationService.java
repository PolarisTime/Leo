package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderPurchaseAllocationService {

    private final PurchaseItemQueryAppService purchaseItemQueryAppService;
    private final PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService;

    public SalesOrderPurchaseAllocationService(PurchaseItemQueryAppService purchaseItemQueryAppService,
                                               PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService) {
        this.purchaseItemQueryAppService = purchaseItemQueryAppService;
        this.purchaseItemPieceWeightAppService = purchaseItemPieceWeightAppService;
    }

    void releaseSalesOrderItems(SalesOrder entity) {
        purchaseItemPieceWeightAppService.releaseSalesOrderItems(
                entity.getItems().stream()
                        .map(SalesOrderItem::getId)
                        .filter(id -> id != null)
                        .toList()
        );
    }

    boolean hasPurchaseOrderBackedItems(SalesOrder entity) {
        return entity.getItems().stream()
                .anyMatch(item -> item.getSourcePurchaseOrderItemId() != null
                        && item.getQuantity() != null
                        && item.getQuantity() > 0);
    }

    void finalizePurchaseOrderAllocations(SalesOrder entity) {
        List<Long> sourcePurchaseOrderItemIds = entity.getItems().stream()
                .map(SalesOrderItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap =
                loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalesOrderItem item : entity.getItems()) {
            BigDecimal weightTon = TradeItemCalculator.scaleWeightTon(item.getWeightTon());
            if (item.getSourcePurchaseOrderItemId() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                int lineNo = item.getLineNo() == null ? 0 : item.getLineNo();
                SourcePurchaseOrderItemRecord sourcePurchaseOrderItem =
                        sourcePurchaseOrderItemMap.get(item.getSourcePurchaseOrderItemId());
                if (sourcePurchaseOrderItem == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
                }
                weightTon = purchaseItemPieceWeightAppService.allocateForSalesOrderItem(
                        sourcePurchaseOrderItem.id(),
                        item.getQuantity(),
                        item.getId(),
                        lineNo
                );
                item.setWeightTon(weightTon);
            }
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, item.getUnitPrice());
            item.setAmount(amount);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }
        entity.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        entity.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private Map<Long, SourcePurchaseOrderItemRecord> loadSourcePurchaseOrderItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        return purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(SourcePurchaseOrderItemRecord::id, item -> item));
    }
}
