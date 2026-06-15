package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SalesOutboundWeightService {

    private static final Logger logger = LoggerFactory.getLogger(SalesOutboundWeightService.class);

    private final JdbcTemplate jdbc;

    public SalesOutboundWeightService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    BigDecimal resolveOutboundWeightTon(SalesOutboundItemRequest source,
                                        SalesOrderItem sourceSalesOrderItem,
                                        Long sourceSalesOrderItemId,
                                        int lineNo) {
        if (sourceSalesOrderItemId == null || source.quantity() == null || source.quantity() <= 0) {
            return fallbackWeightTon(source);
        }
        List<BigDecimal> weights = jdbc.query(
                "SELECT pw.weight_ton FROM po_purchase_order_item_piece_weight pw" +
                        " WHERE pw.sales_order_item_id = ? ORDER BY pw.weight_ton DESC",
                (rs, rowNum) -> rs.getBigDecimal("weight_ton"),
                sourceSalesOrderItemId);
        if (weights.size() < source.quantity()) {
            logger.warn("第{}行逐件记录不足: 需要{}件, 实际{}件, 使用来源销售订单明细均重", lineNo, source.quantity(), weights.size());
            return sourceBackedWeightTon(source, sourceSalesOrderItem, lineNo);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < source.quantity(); i++) {
            total = total.add(weights.get(i));
        }
        return TradeItemCalculator.scaleWeightTon(total);
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
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细重量不可用");
        }
        BigDecimal sourcePieceWeightTon = TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(
                sourceSalesOrderItem.getQuantity(),
                sourceSalesOrderItem.getWeightTon()
        );
        if (sourcePieceWeightTon == null) {
            sourcePieceWeightTon = TradeItemCalculator.calculateAveragePieceWeightTon(
                    sourceSalesOrderItem.getQuantity(),
                    sourceSalesOrderItem.getWeightTon()
            );
        }
        return TradeItemCalculator.calculateWeightTon(source.quantity(), sourcePieceWeightTon);
    }
}
