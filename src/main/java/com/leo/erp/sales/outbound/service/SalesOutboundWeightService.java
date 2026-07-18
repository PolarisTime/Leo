package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
@Service
public class SalesOutboundWeightService {

    BigDecimal resolveOutboundWeightTon(SalesOutboundItemRequest source,
                                        SalesOrderItem sourceSalesOrderItem,
                                        Long sourceSalesOrderItemId,
                                        int lineNo) {
        if (source.weightTon() != null) {
            return TradeItemCalculator.scaleWeightTon(source.weightTon());
        }
        if (sourceSalesOrderItemId == null || source.quantity() == null || source.quantity() <= 0) {
            return fallbackWeightTon(source);
        }
        return sourceBackedWeightTon(source, sourceSalesOrderItem, lineNo);
    }

    private BigDecimal fallbackWeightTon(SalesOutboundItemRequest source) {
        return source.weightTon() == null
                ? TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon())
                : TradeItemCalculator.scaleWeightTon(source.weightTon());
    }

    private BigDecimal sourceBackedWeightTon(SalesOutboundItemRequest source,
                                             SalesOrderItem sourceSalesOrderItem,
                                             int lineNo) {
        if (sourceSalesOrderItem == null
                || sourceSalesOrderItem.getQuantity() == null
                || sourceSalesOrderItem.getQuantity() <= 0
                || sourceSalesOrderItem.getWeightTon() == null) {
            throw new IllegalArgumentException("第" + lineNo + "行来源销售订单明细重量不可用");
        }
        BigDecimal sourcePieceWeightTon = TradeItemCalculator.calculateAveragePieceWeightTon(
                sourceSalesOrderItem.getQuantity(), sourceSalesOrderItem.getWeightTon());
        return TradeItemCalculator.calculateWeightTon(source.quantity(), sourcePieceWeightTon);
    }
}
