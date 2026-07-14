package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import org.springframework.stereotype.Service;

@Service
public class PurchaseInboundDeleteService {

    private final PurchaseInboundWeightWriteBackService weightWriteBackService;

    public PurchaseInboundDeleteService(PurchaseInboundWeightWriteBackService weightWriteBackService) {
        this.weightWriteBackService = weightWriteBackService;
    }

    void afterDelete(PurchaseInbound inbound) {
        weightWriteBackService.synchronizeAfterSave(inbound);
    }
}
