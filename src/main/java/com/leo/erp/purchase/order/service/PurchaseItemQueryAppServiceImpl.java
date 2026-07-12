package com.leo.erp.purchase.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class PurchaseItemQueryAppServiceImpl implements PurchaseItemQueryAppService {

    private final PurchaseInboundItemQueryService inboundItemQueryService;
    private final PurchaseOrderItemQueryService orderItemQueryService;

    public PurchaseItemQueryAppServiceImpl(
            PurchaseInboundItemQueryService inboundItemQueryService,
            PurchaseOrderItemQueryService orderItemQueryService) {
        this.inboundItemQueryService = inboundItemQueryService;
        this.orderItemQueryService = orderItemQueryService;
    }

    @Override
    public List<SourceInboundItemRecord> findSourceInboundItemsByIds(Collection<Long> ids) {
        return inboundItemQueryService.findAllActiveByIdIn(ids).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<SourcePurchaseOrderItemRecord> findSourcePurchaseOrderItemsByIds(Collection<Long> ids) {
        return orderItemQueryService.findActiveByIdIn(ids).stream()
                .map(this::toRecord)
                .toList();
    }

    private SourceInboundItemRecord toRecord(PurchaseInboundItem item) {
        var inbound = item.getPurchaseInbound();
        return new SourceInboundItemRecord(
                item.getId(),
                inbound != null ? inbound.getInboundNo() : null,
                inbound != null ? inbound.getStatus() : null,
                inbound != null ? inbound.getPurchaseOrderNo() : null,
                item.getQuantity(),
                item.getWeighWeightTon(),
                item.getBrand(), item.getMaterial(), item.getSpec(),
                item.getMaterialCode(), item.getCategory(), item.getUnit(),
                item.getWarehouseName(), item.getBatchNo(),
                item.getSettlementCompanyId(), item.getSettlementCompanyName(),
                item.getMaterialId(), item.getWarehouseId(), item.getBatchNoNormalized()
        );
    }

    private SourcePurchaseOrderItemRecord toRecord(PurchaseOrderItem item) {
        var order = item.getPurchaseOrder();
        return new SourcePurchaseOrderItemRecord(
                item.getId(),
                item.getQuantity(),
                item.getWeightTon(),
                item.getPieceWeightTon(),
                order != null ? order.getOrderNo() : null,
                order != null ? order.getStatus() : null,
                item.getBrand(), item.getMaterial(), item.getSpec(),
                item.getMaterialCode(), item.getCategory(), item.getUnit(),
                item.getWarehouseName(), item.getBatchNo(),
                order != null ? order.getSettlementCompanyId() : null,
                order != null ? order.getSettlementCompanyName() : null,
                item.getMaterialId(), item.getWarehouseId(), item.getBatchNoNormalized()
        );
    }
}
