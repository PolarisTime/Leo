package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

@Service
public class PurchaseItemPieceWeightAppServiceImpl implements PurchaseItemPieceWeightAppService {

    private final PurchaseOrderItemPieceWeightService pieceWeightService;
    private final PurchaseOrderItemQueryService orderItemQueryService;

    public PurchaseItemPieceWeightAppServiceImpl(
            PurchaseOrderItemPieceWeightService pieceWeightService,
            PurchaseOrderItemQueryService orderItemQueryService) {
        this.pieceWeightService = pieceWeightService;
        this.orderItemQueryService = orderItemQueryService;
    }

    @Override
    public BigDecimal allocateForSalesOrderItem(
            Long sourcePurchaseOrderItemId, Integer quantity, Long salesOrderItemId, int lineNo) {
        PurchaseOrderItem sourceItem = sourcePurchaseOrderItemId != null
                ? orderItemQueryService.findActiveByIdIn(java.util.List.of(sourcePurchaseOrderItemId))
                        .stream().findFirst().orElse(null)
                : null;
        if (sourceItem == null) {
            return BigDecimal.ZERO;
        }
        return pieceWeightService.allocateForSalesOrderItem(sourceItem, quantity, salesOrderItemId, lineNo);
    }

    @Override
    public void releaseSalesOrderItems(Collection<Long> salesOrderItemIds) {
        pieceWeightService.releaseSalesOrderItems(salesOrderItemIds);
    }

    @Override
    public Map<Long, BigDecimal> summarizeRemainingWeightByPurchaseOrderItemIds(
            Collection<Long> purchaseOrderItemIds) {
        return pieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(purchaseOrderItemIds);
    }
}
