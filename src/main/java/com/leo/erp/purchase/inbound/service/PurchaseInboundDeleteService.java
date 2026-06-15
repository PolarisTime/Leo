package com.leo.erp.purchase.inbound.service;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PurchaseInboundDeleteService {

    private final PurchaseInboundSourceValidator sourceValidator;
    private final PurchaseInboundWeightWriteBackService weightWriteBackService;

    public PurchaseInboundDeleteService(PurchaseInboundSourceValidator sourceValidator,
                                        PurchaseInboundWeightWriteBackService weightWriteBackService) {
        this.sourceValidator = sourceValidator;
        this.weightWriteBackService = weightWriteBackService;
    }

    void beforeDelete(PurchaseInbound inbound) {
        List<Long> sourcePurchaseOrderItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap =
                sourceValidator.loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        weightWriteBackService.writeBackPurchaseOrderWeights(
                sourcePurchaseOrderItemIds,
                inbound.getId(),
                Map.of(),
                sourcePurchaseOrderItemMap
        );
    }
}
