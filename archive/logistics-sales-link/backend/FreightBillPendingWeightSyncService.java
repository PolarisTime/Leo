package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FreightBillPendingWeightSyncService {

    private final FreightBillRepository repository;

    public FreightBillPendingWeightSyncService(FreightBillRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void syncBySalesOutboundItemWeights(Map<Long, BigDecimal> weightBySalesOutboundItemId) {
        Map<Long, BigDecimal> sourceWeights = normalizeWeights(weightBySalesOutboundItemId);
        if (sourceWeights.isEmpty()) {
            return;
        }
        List<Long> sourceItemIds = sourceWeights.keySet().stream().toList();
        List<FreightBill> bills = repository.findAllByStatusAndSourceSalesOutboundItemIds(
                StatusConstants.UNAUDITED,
                sourceItemIds
        );
        if (bills.isEmpty()) {
            return;
        }
        for (FreightBill bill : bills) {
            for (FreightBillItem item : bill.getItems()) {
                BigDecimal sourceWeight = sourceWeights.get(item.getSourceSalesOutboundItemId());
                if (sourceWeight != null) {
                    item.setWeightTon(sourceWeight);
                }
            }
            refreshTotals(bill);
        }
        repository.saveAll(bills);
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

    private void refreshTotals(FreightBill bill) {
        BigDecimal totalWeight = sumWeight(bill.getItems());
        bill.setTotalWeight(totalWeight);
        bill.setTotalFreight(totalWeight
                .multiply(TradeItemCalculator.safeBigDecimal(bill.getUnitPrice()))
                .setScale(PrecisionConstants.AMOUNT_SCALE, PrecisionConstants.DEFAULT_ROUNDING));
    }

    private BigDecimal sumWeight(Collection<FreightBillItem> items) {
        return items.stream()
                .filter(Objects::nonNull)
                .map(item -> TradeItemCalculator.safeBigDecimal(item.getWeightTon()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
