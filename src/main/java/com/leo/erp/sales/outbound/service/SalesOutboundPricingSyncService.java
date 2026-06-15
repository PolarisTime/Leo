package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.service.SalesOrderOutboundPricingSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class SalesOutboundPricingSyncService implements SalesOrderOutboundPricingSyncService {

    private final SalesOutboundRepository salesOutboundRepository;

    public SalesOutboundPricingSyncService(SalesOutboundRepository salesOutboundRepository) {
        this.salesOutboundRepository = salesOutboundRepository;
    }

    @Override
    public void syncAuditedOutboundPricing(Collection<Long> sourceSalesOrderItemIds,
                                           Map<Long, BigDecimal> unitPriceByItemId) {
        if (sourceSalesOrderItemIds == null || sourceSalesOrderItemIds.isEmpty()
                || unitPriceByItemId == null || unitPriceByItemId.isEmpty()) {
            return;
        }

        List<SalesOutbound> outbounds = salesOutboundRepository.findAllByStatusAndSourceSalesOrderItemIds(
                StatusConstants.AUDITED,
                sourceSalesOrderItemIds
        );
        if (outbounds.isEmpty()) {
            return;
        }

        for (SalesOutbound outbound : outbounds) {
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (SalesOutboundItem item : outbound.getItems()) {
                BigDecimal unitPrice = unitPriceByItemId.get(item.getSourceSalesOrderItemId());
                if (unitPrice != null) {
                    item.setUnitPrice(unitPrice);
                    item.setAmount(TradeItemCalculator.calculateAmount(item.getWeightTon(), unitPrice));
                }
                totalAmount = totalAmount.add(TradeItemCalculator.scaleAmount(item.getAmount()));
            }
            outbound.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
        }
        salesOutboundRepository.saveAll(outbounds);
    }
}
