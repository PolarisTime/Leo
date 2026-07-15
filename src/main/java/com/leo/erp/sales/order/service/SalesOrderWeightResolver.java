package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SalesOrderWeightResolver {

    BigDecimal resolvePieceWeightTon(SalesOrderItemRequest source, SalesOrderSourceContext context) {
        return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
    }

    BigDecimal resolveWeightTon(
            SalesOrderItemRequest source,
            BigDecimal pieceWeightTon,
            SalesOrderSourceContext context
    ) {
        BigDecimal defaultWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
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

}
