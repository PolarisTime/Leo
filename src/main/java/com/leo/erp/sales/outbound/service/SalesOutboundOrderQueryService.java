package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.service.SalesOrderOutboundQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class SalesOutboundOrderQueryService implements SalesOrderOutboundQueryService {

    private final SalesOutboundRepository salesOutboundRepository;

    public SalesOutboundOrderQueryService(SalesOutboundRepository salesOutboundRepository) {
        this.salesOutboundRepository = salesOutboundRepository;
    }

    @Override
    public List<OutboundRecord> findAuditedOutboundsBySourceSalesOrderItemIds(
            Collection<Long> sourceSalesOrderItemIds
    ) {
        return salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                        StatusConstants.AUDITED,
                        sourceSalesOrderItemIds
                ).stream()
                .map(this::toRecord)
                .toList();
    }

    private OutboundRecord toRecord(SalesOutbound outbound) {
        return new OutboundRecord(
                outbound.getSalesOrderNo(),
                outbound.getStatus(),
                outbound.getItems().stream().map(this::toItemRecord).toList()
        );
    }

    private OutboundItemRecord toItemRecord(SalesOutboundItem item) {
        return new OutboundItemRecord(
                item.getSourceSalesOrderItemId(),
                item.getQuantity(),
                item.getWeightTon()
        );
    }
}
