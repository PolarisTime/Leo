package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderWeightResolver {

    private final PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService;

    public SalesOrderWeightResolver(PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService) {
        this.purchaseItemPieceWeightAppService = purchaseItemPieceWeightAppService;
    }

    SalesOrderSourceContext withPurchaseOrderRemainingWeights(SalesOrderSourceContext context) {
        return context.withPurchaseOrderRemainingWeightMap(
                loadPurchaseOrderRemainingWeightMap(context.sourcePurchaseOrderItemIds())
        );
    }

    BigDecimal resolvePieceWeightTon(SalesOrderItemRequest source, SalesOrderSourceContext context) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        SourcePurchaseOrderItemRecord sourcePurchaseOrderItem =
                context.sourcePurchaseOrderItemMap().get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        BigDecimal sourcePieceWeightTon = TradeItemCalculator.scaleWeightTon(sourcePurchaseOrderItem.pieceWeightTon());
        if (sourcePieceWeightTon.compareTo(BigDecimal.ZERO) <= 0) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        return sourcePieceWeightTon;
    }

    BigDecimal resolveWeightTon(
            SalesOrderItemRequest source,
            BigDecimal pieceWeightTon,
            SalesOrderSourceContext context
    ) {
        BigDecimal defaultWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId != null) {
            return defaultWeightTon;
        }
        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null || source.quantity() == null || source.quantity() <= 0) {
            return defaultWeightTon;
        }
        SourceInboundItemRecord sourceInboundItem = context.sourceInboundItemMap().get(sourceInboundItemId);
        if (sourceInboundItem == null || sourceInboundItem.weighWeightTon() == null) {
            return defaultWeightTon;
        }
        int sourceQuantity = sourceInboundItem.quantity() == null ? 0 : sourceInboundItem.quantity();
        if (sourceQuantity <= 0) {
            return defaultWeightTon;
        }
        SalesOrderSourceAllocation persistedAllocation =
                context.inboundAllocatedMap().getOrDefault(sourceInboundItemId, SalesOrderSourceAllocation.ZERO);
        SalesOrderSourceAllocation requestAllocation =
                context.requestInboundAllocatedMap().getOrDefault(sourceInboundItemId, SalesOrderSourceAllocation.ZERO);
        int allocatedQuantityAfterCurrent = persistedAllocation.quantity() + requestAllocation.quantity() + source.quantity();
        if (allocatedQuantityAfterCurrent < sourceQuantity) {
            return defaultWeightTon;
        }
        BigDecimal residualWeightTon = TradeItemCalculator.scaleWeightTon(
                sourceInboundItem.weighWeightTon()
                        .subtract(persistedAllocation.weightTon())
                        .subtract(requestAllocation.weightTon())
        );
        return residualWeightTon.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE)
                : residualWeightTon;
    }

    private Map<Long, BigDecimal> loadPurchaseOrderRemainingWeightMap(List<Long> sourcePurchaseOrderItemIds) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> remainingWeightMap =
                purchaseItemPieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(sourcePurchaseOrderItemIds);
        return remainingWeightMap == null ? Map.of() : remainingWeightMap;
    }
}
