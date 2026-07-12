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
import java.util.Objects;

@Service
public class SalesOrderProtectedUpdatePolicy {

    private final SalesOrderAuditedPricingService salesOrderAuditedPricingService;

    public SalesOrderProtectedUpdatePolicy(SalesOrderAuditedPricingService salesOrderAuditedPricingService) {
        this.salesOrderAuditedPricingService = salesOrderAuditedPricingService;
    }

    boolean allowsProtectedUpdate(SalesOrder entity, SalesOrderRequest request) {
        String currentStatus = normalize(entity.getStatus());
        if (StatusConstants.DELIVERY_VERIFICATION.equals(currentStatus)) {
            return StatusConstants.DELIVERY_VERIFICATION.equals(normalize(request.status()))
                    && salesOrderAuditedPricingService.matchesAuditedPricingUpdate(entity, request);
        }
        if (!StatusConstants.AUDITED.equals(currentStatus)) {
            return false;
        }
        String nextStatus = normalize(request.status());
        if (StatusConstants.DRAFT.equals(nextStatus)) {
            return matchesStatusOnlyUpdate(entity, request);
        }
        if (!StatusConstants.AUDITED.equals(nextStatus)) {
            return false;
        }
        return salesOrderAuditedPricingService.matchesAuditedPricingUpdate(entity, request);
    }

    private boolean matchesStatusOnlyUpdate(SalesOrder entity, SalesOrderRequest request) {
        if (entity == null || request == null) {
            return false;
        }
        if (!normalize(entity.getOrderNo()).equals(normalize(request.orderNo()))
                || !normalize(entity.getPurchaseInboundNo()).equals(normalize(request.purchaseInboundNo()))
                || !normalize(entity.getPurchaseOrderNo()).equals(normalize(request.purchaseOrderNo()))
                || !normalize(entity.getCustomerCode()).equals(normalize(request.customerCode()))
                || !Objects.equals(entity.getCustomerId(), request.customerId())
                || !normalize(entity.getCustomerName()).equals(normalize(request.customerName()))
                || !Objects.equals(entity.getProjectId(), request.projectId())
                || !normalize(entity.getProjectName()).equals(normalize(request.projectName()))
                || !Objects.equals(entity.getDeliveryDate(), request.deliveryDate())
                || !normalize(entity.getSalesName()).equals(normalize(request.salesName()))
                || !normalize(entity.getRemark()).equals(normalize(request.remark()))) {
            return false;
        }

        List<SalesOrderItem> entityItems = entity.getItems().stream()
                .sorted(java.util.Comparator.comparing(SalesOrderItem::getLineNo))
                .toList();
        List<SalesOrderItemRequest> requestItems = request.items() == null ? List.of() : request.items();
        if (entityItems.size() != requestItems.size()) {
            return false;
        }
        for (int i = 0; i < entityItems.size(); i++) {
            if (!matchesStatusOnlyUpdateItem(entityItems.get(i), requestItems.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesStatusOnlyUpdateItem(SalesOrderItem entityItem, SalesOrderItemRequest requestItem) {
        return Objects.equals(entityItem.getId(), requestItem.id())
                && Objects.equals(entityItem.getMaterialId(), requestItem.materialId())
                && normalize(entityItem.getMaterialCode()).equals(normalize(requestItem.materialCode()))
                && normalize(entityItem.getBrand()).equals(normalize(requestItem.brand()))
                && normalize(entityItem.getCategory()).equals(normalize(requestItem.category()))
                && normalize(entityItem.getMaterial()).equals(normalize(requestItem.material()))
                && normalize(entityItem.getSpec()).equals(normalize(requestItem.spec()))
                && normalize(entityItem.getLength()).equals(normalize(requestItem.length()))
                && normalize(entityItem.getUnit()).equals(normalize(requestItem.unit()))
                && Objects.equals(entityItem.getSourceInboundItemId(), requestItem.sourceInboundItemId())
                && Objects.equals(entityItem.getSourcePurchaseOrderItemId(), requestItem.sourcePurchaseOrderItemId())
                && Objects.equals(entityItem.getWarehouseId(), requestItem.warehouseId())
                && normalize(entityItem.getWarehouseName()).equals(normalize(requestItem.warehouseName()))
                && normalize(entityItem.getBatchNo()).equals(normalize(requestItem.batchNo()))
                && Objects.equals(entityItem.getQuantity(), requestItem.quantity())
                && TradeItemCalculator.normalizeQuantityUnit(entityItem.getQuantityUnit())
                .equals(TradeItemCalculator.normalizeQuantityUnit(requestItem.quantityUnit()))
                && compareWeight(entityItem.getPieceWeightTon(), requestItem.pieceWeightTon())
                && Objects.equals(entityItem.getPiecesPerBundle(), requestItem.piecesPerBundle())
                && compareWeight(entityItem.getWeightTon(), requestItem.weightTon())
                && compareAmount(entityItem.getUnitPrice(), requestItem.unitPrice())
                && compareAmount(entityItem.getAmount(), requestItem.amount());
    }

    private boolean compareWeight(BigDecimal left, BigDecimal right) {
        return TradeItemCalculator.scaleWeightTon(left).compareTo(TradeItemCalculator.scaleWeightTon(right)) == 0;
    }

    private boolean compareAmount(BigDecimal left, BigDecimal right) {
        return TradeItemCalculator.scaleAmount(left).compareTo(TradeItemCalculator.scaleAmount(right)) == 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
