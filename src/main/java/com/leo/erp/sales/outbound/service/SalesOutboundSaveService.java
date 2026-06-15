package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.springframework.stereotype.Service;

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
        if (completionSyncService != null) {
            completionSyncService.syncBySalesOrderReference(saved.getSalesOrderNo());
        }
        return saved;
    }
}
