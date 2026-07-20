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
        List<PurchaseInboundItem> items = inboundItemQueryService.findAllActiveByIdIn(ids);
        var purchaseOrderStatusByItemId = purchaseOrderStatusByItemId(items);
        return items.stream()
                .map(item -> toRecord(item, sourcePurchaseOrderStatus(item, purchaseOrderStatusByItemId)))
                .toList();
    }

    @Override
    public List<SourcePurchaseOrderItemRecord> findSourcePurchaseOrderItemsByIds(Collection<Long> ids) {
        return orderItemQueryService.findActiveByIdIn(ids).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<SourcePurchaseOrderItemRecord> findPurchaseOrderItemSnapshotsByIds(Collection<Long> ids) {
        return orderItemQueryService.findSnapshotsByIdIn(ids).stream()
                .map(this::toRecord)
                .toList();
    }

    private SourceInboundItemRecord toRecord(PurchaseInboundItem item, String purchaseOrderStatus) {
        var inbound = item.getPurchaseInbound();
        return new SourceInboundItemRecord(
                item.getId(),
                inbound != null ? inbound.getInboundNo() : null,
                inbound != null ? inbound.getStatus() : null,
                inbound != null ? inbound.getPurchaseOrderNo() : null,
                purchaseOrderStatus,
                item.getQuantity(),
                item.getWeighWeightTon(),
                item.getBrand(), item.getMaterial(), item.getSpec(),
                item.getMaterialCode(), item.getCategory(), item.getUnit(),
                item.getWarehouseName(), item.getBatchNo(),
                item.getSettlementCompanyId(), item.getSettlementCompanyName(),
                item.getMaterialId(), item.getWarehouseId(), item.getBatchNoNormalized(),
                item.getLength(), item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle()
        );
    }

    private java.util.Map<Long, String> purchaseOrderStatusByItemId(List<PurchaseInboundItem> items) {
        List<Long> sourceItemIds = items.stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (sourceItemIds.isEmpty()) {
            return java.util.Map.of();
        }
        return orderItemQueryService.findActiveByIdIn(sourceItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PurchaseOrderItem::getId,
                        sourceItem -> sourceItem.getPurchaseOrder().getStatus()
                ));
    }

    private String sourcePurchaseOrderStatus(PurchaseInboundItem item,
                                             java.util.Map<Long, String> statusBySourceItemId) {
        Long sourceItemId = item.getSourcePurchaseOrderItemId();
        return sourceItemId == null ? null : statusBySourceItemId.get(sourceItemId);
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
                item.getMaterialId(), item.getWarehouseId(), item.getBatchNoNormalized(),
                item.getLength(), item.getQuantityUnit(), item.getPiecesPerBundle()
        );
    }
}
