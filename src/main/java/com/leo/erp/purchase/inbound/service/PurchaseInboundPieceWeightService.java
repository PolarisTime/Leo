package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PurchaseInboundPieceWeightService {

    private static final BigDecimal WEIGHT_UNIT = new BigDecimal("0.001");

    private final PurchaseInboundItemQueryService itemQueryService;

    @Autowired
    public PurchaseInboundPieceWeightService(PurchaseInboundItemQueryService itemQueryService) {
        this.itemQueryService = itemQueryService;
    }

    @Transactional(readOnly = true)
    public List<PieceWeightResponse> getPieceWeights(Long itemId) {
        PurchaseInboundItem item = itemQueryService.requireActiveById(itemId);
        int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        if (quantity <= 0) {
            return List.of();
        }
        BigDecimal totalWeightTon = TradeItemCalculator.scaleWeightTon(
                item.getWeighWeightTon() != null ? item.getWeighWeightTon() : item.getWeightTon()
        );
        BigDecimal averageWeightTon = TradeItemCalculator.calculateAveragePieceWeightTon(quantity, totalWeightTon);
        BigDecimal averageTotalWeightTon = averageWeightTon.multiply(BigDecimal.valueOf(quantity));
        int residualUnits = totalWeightTon.subtract(averageTotalWeightTon)
                .divide(WEIGHT_UNIT)
                .intValue();
        int residualCount = Math.min(Math.abs(residualUnits), quantity);
        BigDecimal adjustment = residualUnits > 0 ? WEIGHT_UNIT : WEIGHT_UNIT.negate();

        java.util.ArrayList<PieceWeightResponse> responses = new java.util.ArrayList<>(quantity);
        for (int i = 0; i < quantity; i++) {
            BigDecimal pieceWeightTon = averageWeightTon;
            if (residualUnits != 0 && i >= quantity - residualCount) {
                pieceWeightTon = pieceWeightTon.add(adjustment);
            }
            responses.add(new PieceWeightResponse(
                    i + 1,
                    TradeItemCalculator.scaleWeightTon(pieceWeightTon),
                    item.getPurchaseInbound() == null ? "" : item.getPurchaseInbound().getInboundNo()
            ));
        }
        return responses;
    }
}
