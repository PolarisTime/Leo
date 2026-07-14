package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

@Service
public class SalesOutboundApplyService {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final SalesOutboundSourceService sourceService;
    private final SalesOutboundWeightService weightService;

    public SalesOutboundApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                     WarehouseSelectionSupport warehouseSelectionSupport,
                                     SalesOutboundSourceService sourceService,
                                     SalesOutboundWeightService weightService) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.sourceService = sourceService;
        this.weightService = weightService;
    }

    void applyItems(SalesOutbound entity,
                    SalesOutboundRequest request,
                    LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalesOutboundItem> managedItems = entity.getItems();
        List<SalesOutboundItem> items = ManagedEntityItemSupport.syncById(
                new ArrayList<>(managedItems),
                request.items(),
                SalesOutboundItem::getId,
                SalesOutboundItemRequest::id,
                SalesOutboundItem::new,
                nextIdSupplier,
                SalesOutboundItem::setId
        );
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap =
                sourceService.loadSourceSalesOrderItemMap(request.items(), items);
        Map<Long, Integer> requestSourceQuantityMap = new java.util.HashMap<>();
        LinkedHashSet<String> sourceSalesOrderNos = new LinkedHashSet<>();
        LinkedHashSet<Long> sourceSalesOrderItemIds = new LinkedHashSet<>();
        LinkedHashSet<SettlementCompanySnapshot> salesSettlementCompanies = new LinkedHashSet<>();
        LinkedHashSet<Long> customerIds = new LinkedHashSet<>();
        LinkedHashSet<Long> projectIds = new LinkedHashSet<>();
        LinkedHashSet<Long> warehouseIds = new LinkedHashSet<>();
        LinkedHashSet<String> warehouseNames = new LinkedHashSet<>();

        for (int i = 0; i < request.items().size(); i++) {
            SalesOutboundItemRequest source = request.items().get(i);
            SalesOutboundItem item = items.get(i);
            int lineNo = i + 1;
            LineApplyResult result = applyItem(
                    entity,
                    request,
                    source,
                    item,
                    lineNo,
                    sourceSalesOrderItemMap,
                    requestSourceQuantityMap
            );
            sourceSalesOrderItemIds.add(result.sourceSalesOrderItemId());
            sourceService.collectSourceSalesOrderNos(
                    sourceSalesOrderNos,
                    source,
                    sourceSalesOrderItemMap,
                    result.sourceSalesOrderItemId()
            );
            collectSalesSettlementCompany(salesSettlementCompanies, result.sourceSalesOrderItemId(), sourceSalesOrderItemMap);
            SalesOrderItem sourceItem = result.sourceSalesOrderItemId() == null
                    ? null
                    : sourceSalesOrderItemMap.get(result.sourceSalesOrderItemId());
            if (sourceItem != null && sourceItem.getSalesOrder() != null) {
                addIfPresent(customerIds, sourceItem.getSalesOrder().getCustomerId());
                addIfPresent(projectIds, sourceItem.getSalesOrder().getProjectId());
            }
            addIfPresent(warehouseIds, item.getWarehouseId());
            addIfPresent(warehouseNames, result.warehouseName());
            totalWeight = totalWeight.add(result.weightTon());
            totalAmount = totalAmount.add(result.amount());
        }

        sourceService.assertSourceSalesOrderItemsNotOccupied(sourceSalesOrderItemIds, entity.getId());
        managedItems.clear();
        managedItems.addAll(items);
        managedItems.sort(java.util.Comparator.comparing(SalesOutboundItem::getLineNo));
        entity.setSalesOrderNo(sourceSalesOrderNos.isEmpty()
                ? trimToNull(request.salesOrderNo())
                : String.join(", ", sourceSalesOrderNos));
        applyHeaderSettlementCompany(entity, salesSettlementCompanies);
        entity.setCustomerId(resolveSingleIdentity(customerIds, request.customerId(), "客户"));
        entity.setProjectId(resolveSingleIdentity(projectIds, request.projectId(), "项目"));
        entity.setWarehouseId(resolveWarehouseId(warehouseIds, request.warehouseId()));
        entity.setWarehouseName(resolveWarehouseName(warehouseIds, warehouseNames, request.warehouseName()));
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    List<Long> sourceSalesOrderIds(SalesOutbound entity) {
        return sourceService.loadSourceSalesOrderItemMap(entity.getItems()).values().stream()
                .map(SalesOrderItem::getSalesOrder)
                .filter(Objects::nonNull)
                .map(com.leo.erp.sales.order.domain.entity.SalesOrder::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private LineApplyResult applyItem(SalesOutbound entity,
                                      SalesOutboundRequest request,
                                      SalesOutboundItemRequest source,
                                      SalesOutboundItem item,
                                      int lineNo,
                                      Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                      Map<Long, Integer> requestSourceQuantityMap) {
        item.setSalesOutbound(entity);
        item.setLineNo(lineNo);
        Long sourceSalesOrderItemId = sourceService.resolveSourceSalesOrderItemId(source, item, lineNo);
        SalesOrderItem sourceSalesOrderItem =
                sourceService.resolveSourceSalesOrderItem(sourceSalesOrderItemMap, sourceSalesOrderItemId, lineNo);
        TradeMaterialSnapshot material = tradeItemMaterialSupport.resolveMaterial(
                source.materialId() == null ? sourceSalesOrderItem.getMaterialId() : source.materialId(),
                source.materialCode(),
                lineNo
        );
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        item.setSettlementCompanyId(sourceSalesOrderItem.getSettlementCompanyId());
        item.setSettlementCompanyName(sourceSalesOrderItem.getSettlementCompanyName());
        item.setMaterialId(sourceSalesOrderItem.getMaterialId() == null
                ? material.materialId() : sourceSalesOrderItem.getMaterialId());
        item.setMaterialCode(sourceSalesOrderItem.getMaterialCode());
        item.setBrand(sourceSalesOrderItem.getBrand());
        item.setCategory(sourceSalesOrderItem.getCategory());
        item.setMaterial(sourceSalesOrderItem.getMaterial());
        item.setSpec(sourceSalesOrderItem.getSpec());
        item.setLength(sourceSalesOrderItem.getLength());
        item.setUnit(sourceSalesOrderItem.getUnit());
        String requestedWarehouseName = source.warehouseName() == null || source.warehouseName().isBlank()
                ? request.warehouseName() : source.warehouseName();
        WarehouseSnapshot warehouse = sourceSalesOrderItem.getSalesOrder() != null
                && (sourceSalesOrderItem.getWarehouseId() != null
                || trimToNull(sourceSalesOrderItem.getWarehouseName()) != null)
                ? new WarehouseSnapshot(
                        sourceSalesOrderItem.getWarehouseId(),
                        null,
                        sourceSalesOrderItem.getWarehouseName()
                )
                : warehouseSelectionSupport.resolveWarehouse(
                        source.warehouseId() == null ? request.warehouseId() : source.warehouseId(),
                        requestedWarehouseName,
                        lineNo,
                        true
                );
        item.setWarehouseId(warehouse.warehouseId());
        item.setWarehouseName(warehouse.warehouseName());
        item.setBatchNo(sourceSalesOrderItem.getBatchNo());
        item.setQuantity(source.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        sourceService.validateSourceSalesOrderItem(
                source,
                sourceSalesOrderItem,
                sourceSalesOrderItemId,
                request.customerId(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                source.warehouseId() == null ? request.warehouseId() : source.warehouseId(),
                warehouse.warehouseName(),
                item.getBatchNo(),
                requestSourceQuantityMap,
                lineNo
        );
        BigDecimal weightTon = weightService.resolveOutboundWeightTon(
                source,
                sourceSalesOrderItem,
                sourceSalesOrderItemId,
                lineNo
        );
        item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(source.pieceWeightTon()));
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setWeightTon(weightTon);
        BigDecimal sourceUnitPrice = TradeItemCalculator.scaleAmount(sourceSalesOrderItem.getUnitPrice());
        item.setUnitPrice(sourceUnitPrice);
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, sourceUnitPrice);
        item.setAmount(amount);
        return new LineApplyResult(sourceSalesOrderItemId, warehouse.warehouseName(), weightTon, amount);
    }

    private void collectSalesSettlementCompany(
            LinkedHashSet<SettlementCompanySnapshot> snapshots,
            Long sourceSalesOrderItemId,
            Map<Long, SalesOrderItem> sourceSalesOrderItemMap
    ) {
        if (sourceSalesOrderItemId == null) {
            return;
        }
        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
        if (sourceSalesOrderItem == null || sourceSalesOrderItem.getSalesOrder() == null) {
            return;
        }
        snapshots.add(new SettlementCompanySnapshot(
                sourceSalesOrderItem.getSalesOrder().getSettlementCompanyId(),
                trimToNull(sourceSalesOrderItem.getSalesOrder().getSettlementCompanyName())
        ));
    }

    private void applyHeaderSettlementCompany(
            SalesOutbound entity,
            LinkedHashSet<SettlementCompanySnapshot> snapshots
    ) {
        List<SettlementCompanySnapshot> effectiveSnapshots = snapshots.stream()
                .filter(snapshot -> snapshot.id() != null || snapshot.name() != null)
                .distinct()
                .toList();
        if (effectiveSnapshots.isEmpty()) {
            entity.setSettlementCompanyId(null);
            entity.setSettlementCompanyName(null);
            return;
        }
        if (effectiveSnapshots.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单存在不同客户结算主体，不能合并生成销售出库单");
        }
        SettlementCompanySnapshot snapshot = effectiveSnapshots.get(0);
        entity.setSettlementCompanyId(snapshot.id());
        entity.setSettlementCompanyName(snapshot.name());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void addIfPresent(LinkedHashSet<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
    }

    private void addIfPresent(LinkedHashSet<String> values, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private Long resolveWarehouseId(LinkedHashSet<Long> warehouseIds, Long fallback) {
        if (warehouseIds.size() > 1) {
            return null;
        }
        return warehouseIds.isEmpty() ? fallback : warehouseIds.iterator().next();
    }

    private String resolveWarehouseName(LinkedHashSet<Long> warehouseIds,
                                        LinkedHashSet<String> warehouseNames,
                                        String fallback) {
        if (warehouseIds.size() > 1 || warehouseNames.size() > 1) {
            return "多仓库";
        }
        return warehouseNames.isEmpty() ? trimToNull(fallback) : warehouseNames.iterator().next();
    }

    private Long resolveSingleIdentity(LinkedHashSet<Long> values, Long fallback, String fieldName) {
        if (values.size() > 1) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "来源销售订单存在不同" + fieldName + "，不能合并生成销售出库单"
            );
        }
        return values.isEmpty() ? fallback : values.iterator().next();
    }

    private record LineApplyResult(
            Long sourceSalesOrderItemId,
            String warehouseName,
            BigDecimal weightTon,
            BigDecimal amount
    ) {
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }
}
