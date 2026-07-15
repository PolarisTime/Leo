package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesOutboundDraftWeightSyncService {

    private final SalesOutboundRepository repository;

    public SalesOutboundDraftWeightSyncService(SalesOutboundRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void syncBySalesOrderItemWeights(Map<Long, BigDecimal> weightBySalesOrderItemId) {
        Map<Long, BigDecimal> sourceWeights = normalizeWeights(weightBySalesOrderItemId);
        if (sourceWeights.isEmpty()) {
            return;
        }
        List<SalesOutbound> outbounds = repository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.DRAFT,
                sourceWeights.keySet()
        );
        for (SalesOutbound outbound : outbounds) {
            applyWeights(outbound, sourceWeights);
        }
        repository.saveAll(outbounds);
    }

    private void applyWeights(SalesOutbound outbound, Map<Long, BigDecimal> sourceWeights) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalesOutboundItem item : outbound.getItems()) {
            BigDecimal sourceWeight = sourceWeights.get(item.getSourceSalesOrderItemId());
            if (sourceWeight != null) {
                item.setWeightTon(sourceWeight);
            }
            BigDecimal weight = TradeItemCalculator.scaleWeightTon(item.getWeightTon());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weight, item.getUnitPrice());
            item.setAmount(amount);
            totalWeight = totalWeight.add(weight);
            totalAmount = totalAmount.add(amount);
        }
        outbound.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        outbound.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private Map<Long, BigDecimal> normalizeWeights(Map<Long, BigDecimal> sourceWeights) {
        if (sourceWeights == null || sourceWeights.isEmpty()) {
            return Map.of();
        }
        return sourceWeights.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> TradeItemCalculator.scaleWeightTon(entry.getValue()),
                        (left, ignored) -> left
                ));
    }
}
