package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SalesOrderPurchaseAllocationService {

    private final PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService;

    public SalesOrderPurchaseAllocationService(PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService) {
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

    void finalizeInboundSourceAllocations(SalesOrder entity) {
        BigDecimal totalWeightTon = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalesOrderItem item : entity.getItems()) {
            BigDecimal weightTon = TradeItemCalculator.scaleWeightTon(item.getWeightTon());
            if (item.getSourceInboundItemId() != null) {
                int lineNo = item.getLineNo() == null ? 0 : item.getLineNo();
                weightTon = purchaseItemPieceWeightAppService.allocateForInboundSourceSalesOrderItem(
                        item.getSourceInboundItemId(),
                        item.getQuantity(),
                        item.getId(),
                        lineNo
                );
                item.setWeightTon(weightTon);
            }
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, item.getUnitPrice());
            item.setAmount(amount);
            totalWeightTon = totalWeightTon.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }
        entity.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeightTon));
        entity.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

}
