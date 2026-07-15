package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.service.FreightBillPendingWeightSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesOutboundPreOutboundWeightSyncService {

    private final SalesOutboundRepository repository;
    private final FreightBillPendingWeightSyncService freightBillWeightSyncService;

    public SalesOutboundPreOutboundWeightSyncService(SalesOutboundRepository repository,
                                                     FreightBillPendingWeightSyncService freightBillWeightSyncService) {
        this.repository = repository;
        this.freightBillWeightSyncService = freightBillWeightSyncService;
    }

    @Transactional
    public void syncBySalesOrderItemWeights(Map<Long, BigDecimal> weightBySalesOrderItemId) {
        Map<Long, BigDecimal> sourceWeights = normalizeWeights(weightBySalesOrderItemId);
        if (sourceWeights.isEmpty()) {
            return;
        }
        List<Long> sourceItemIds = sourceWeights.keySet().stream().toList();
        List<SalesOutbound> outbounds = repository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.PRE_OUTBOUND,
                sourceItemIds
        );
        if (outbounds.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> outboundItemWeights = new LinkedHashMap<>();
        for (SalesOutbound outbound : outbounds) {
            for (SalesOutboundItem item : outbound.getItems()) {
                BigDecimal sourceWeight = sourceWeights.get(item.getSourceSalesOrderItemId());
                if (sourceWeight == null) {
                    continue;
                }
                item.setWeightTon(sourceWeight);
                item.setAmount(TradeItemCalculator.calculateAmount(sourceWeight, item.getUnitPrice()));
                if (item.getId() != null) {
                    outboundItemWeights.put(item.getId(), sourceWeight);
                }
            }
            refreshTotals(outbound);
        }
        repository.saveAll(outbounds);
        freightBillWeightSyncService.syncBySalesOutboundItemWeights(outboundItemWeights);
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

    private void refreshTotals(SalesOutbound outbound) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalesOutboundItem item : outbound.getItems()) {
            if (item == null) {
                continue;
            }
            BigDecimal weightTon = TradeItemCalculator.scaleWeightTon(item.getWeightTon());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, item.getUnitPrice());
            item.setWeightTon(weightTon);
            item.setAmount(amount);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }
        outbound.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        outbound.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }
}
