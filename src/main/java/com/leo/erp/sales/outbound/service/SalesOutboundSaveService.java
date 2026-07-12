package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SalesOutboundSaveService {

    private final SalesOutboundRepository repository;
    private final SalesOrderCompletionSyncService completionSyncService;

    public SalesOutboundSaveService(SalesOutboundRepository repository,
                                    SalesOrderCompletionSyncService completionSyncService) {
        this.repository = repository;
        this.completionSyncService = completionSyncService;
    }

    SalesOutbound save(SalesOutbound entity) {
        SalesOutbound saved = repository.save(entity);
        List<Long> sourceItemIds = sourceSalesOrderItemIds(saved);
        if (completionSyncService != null && !sourceItemIds.isEmpty()) {
            completionSyncService.syncBySourceSalesOrderItemIds(sourceItemIds);
        }
        return saved;
    }

    private List<Long> sourceSalesOrderItemIds(SalesOutbound outbound) {
        if (outbound.getItems() == null) {
            return List.of();
        }
        return outbound.getItems().stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }
}
