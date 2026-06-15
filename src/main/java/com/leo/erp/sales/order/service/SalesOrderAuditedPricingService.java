package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SalesOrderAuditedPricingService {

    private final SalesOrderOutboundPricingSyncService outboundPricingSyncService;

    public SalesOrderAuditedPricingService(SalesOrderOutboundPricingSyncService outboundPricingSyncService) {
        this.outboundPricingSyncService = outboundPricingSyncService;
    }

    boolean isAuditedPricingUpdate(SalesOrder entity, SalesOrderRequest request) {
        return StatusConstants.AUDITED.equals(normalize(entity.getStatus()))
                && StatusConstants.AUDITED.equals(normalize(request.status()))
                && matchesAuditedPricingUpdate(entity, request);
    }

    boolean matchesAuditedPricingUpdate(SalesOrder entity, SalesOrderRequest request) {
        if (entity == null || request == null) {
            return false;
        }
        if (!normalize(entity.getOrderNo()).equals(normalize(request.orderNo()))
                || !normalize(entity.getPurchaseInboundNo()).equals(normalize(request.purchaseInboundNo()))
                || !normalize(entity.getPurchaseOrderNo()).equals(normalize(request.purchaseOrderNo()))
                || !normalize(entity.getCustomerCode()).equals(normalize(request.customerCode()))
                || !normalize(entity.getCustomerName()).equals(normalize(request.customerName()))
                || !Objects.equals(entity.getProjectId(), request.projectId())
                || !normalize(entity.getProjectName()).equals(normalize(request.projectName()))
                || !normalize(entity.getSalesName()).equals(normalize(request.salesName()))) {
            return false;
        }

        List<SalesOrderItem> entityItems = sortedItems(entity);
        List<SalesOrderItemRequest> requestItems = request.items() == null ? List.of() : request.items();
        if (entityItems.size() != requestItems.size()) {
            return false;
        }
        for (int i = 0; i < entityItems.size(); i++) {
            if (!matchesPricingUpdateItem(entityItems.get(i), requestItems.get(i))) {
                return false;
            }
        }
        return true;
    }

    void applyAuditedPricingUpdate(SalesOrder entity, SalesOrderRequest request) {
        Map<Long, SalesOrderItemRequest> requestItemMap = request.items().stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderItemRequest::id, item -> item));

        BigDecimal totalAmount = BigDecimal.ZERO;
        entity.setDeliveryDate(request.deliveryDate());
        entity.setRemark(request.remark());
        for (SalesOrderItem item : entity.getItems()) {
            SalesOrderItemRequest requestItem = requestItemMap.get(item.getId());
            BigDecimal unitPrice = TradeItemCalculator.scaleAmount(requestItem.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(item.getWeightTon(), unitPrice);
            item.setUnitPrice(unitPrice);
            item.setAmount(amount);
            totalAmount = totalAmount.add(amount);
        }
        entity.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
        syncAuditedSalesOutboundPricing(entity);
    }

    private boolean matchesPricingUpdateItem(SalesOrderItem entityItem, SalesOrderItemRequest requestItem) {
        return Objects.equals(entityItem.getId(), requestItem.id())
                && normalize(entityItem.getMaterialCode()).equals(normalize(requestItem.materialCode()))
                && normalize(entityItem.getBrand()).equals(normalize(requestItem.brand()))
                && normalize(entityItem.getCategory()).equals(normalize(requestItem.category()))
                && normalize(entityItem.getMaterial()).equals(normalize(requestItem.material()))
                && normalize(entityItem.getSpec()).equals(normalize(requestItem.spec()))
                && normalize(entityItem.getLength()).equals(normalize(requestItem.length()))
                && normalize(entityItem.getUnit()).equals(normalize(requestItem.unit()))
                && Objects.equals(entityItem.getSourceInboundItemId(), requestItem.sourceInboundItemId())
                && Objects.equals(entityItem.getSourcePurchaseOrderItemId(), requestItem.sourcePurchaseOrderItemId())
                && normalize(entityItem.getWarehouseName()).equals(normalize(requestItem.warehouseName()))
                && normalize(entityItem.getBatchNo()).equals(normalize(requestItem.batchNo()))
                && Objects.equals(entityItem.getQuantity(), requestItem.quantity())
                && TradeItemCalculator.normalizeQuantityUnit(entityItem.getQuantityUnit())
                .equals(TradeItemCalculator.normalizeQuantityUnit(requestItem.quantityUnit()))
                && compareWeight(entityItem.getPieceWeightTon(), requestItem.pieceWeightTon())
                && Objects.equals(entityItem.getPiecesPerBundle(), requestItem.piecesPerBundle())
                && compareWeight(entityItem.getWeightTon(), requestItem.weightTon());
    }

    private void syncAuditedSalesOutboundPricing(SalesOrder entity) {
        if (outboundPricingSyncService == null) {
            return;
        }
        List<Long> sourceSalesOrderItemIds = entity.getItems().stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .toList();
        if (sourceSalesOrderItemIds.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> unitPriceByItemId = entity.getItems().stream()
                .filter(item -> item.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SalesOrderItem::getId,
                        item -> TradeItemCalculator.scaleAmount(item.getUnitPrice())
                ));
        outboundPricingSyncService.syncAuditedOutboundPricing(sourceSalesOrderItemIds, unitPriceByItemId);
    }

    private List<SalesOrderItem> sortedItems(SalesOrder entity) {
        return entity.getItems().stream()
                .sorted(java.util.Comparator.comparing(SalesOrderItem::getLineNo))
                .toList();
    }

    private boolean compareWeight(BigDecimal left, BigDecimal right) {
        return TradeItemCalculator.scaleWeightTon(left).compareTo(TradeItemCalculator.scaleWeightTon(right)) == 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
