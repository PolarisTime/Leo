package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        String firstLineWarehouseName = null;
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(SalesOutboundItemRequest::materialCode).toList()
        );
        List<SalesOutboundItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
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
                    materialMap.get(source.materialCode()),
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
            totalWeight = totalWeight.add(result.weightTon());
            totalAmount = totalAmount.add(result.amount());
            if (firstLineWarehouseName == null) {
                firstLineWarehouseName = result.warehouseName();
            }
        }

        sourceService.assertSourceSalesOrderItemsNotOccupied(sourceSalesOrderItemIds, entity.getId());
        entity.getItems().sort(java.util.Comparator.comparing(SalesOutboundItem::getLineNo));
        entity.setSalesOrderNo(sourceSalesOrderNos.isEmpty()
                ? trimToNull(request.salesOrderNo())
                : String.join(", ", sourceSalesOrderNos));
        applyHeaderSettlementCompany(entity, salesSettlementCompanies);
        entity.setWarehouseName(firstLineWarehouseName == null ? trimToNull(request.warehouseName()) : firstLineWarehouseName);
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    private LineApplyResult applyItem(SalesOutbound entity,
                                      SalesOutboundRequest request,
                                      SalesOutboundItemRequest source,
                                      SalesOutboundItem item,
                                      int lineNo,
                                      TradeMaterialSnapshot material,
                                      Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                      Map<Long, Integer> requestSourceQuantityMap) {
        item.setSalesOutbound(entity);
        item.setLineNo(lineNo);
        Long sourceSalesOrderItemId = sourceService.resolveSourceSalesOrderItemId(source, item, lineNo);
        SalesOrderItem sourceSalesOrderItem =
                sourceService.resolveSourceSalesOrderItem(sourceSalesOrderItemMap, sourceSalesOrderItemId, lineNo);
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        item.setSettlementCompanyId(sourceSalesOrderItem.getSettlementCompanyId());
        item.setSettlementCompanyName(sourceSalesOrderItem.getSettlementCompanyName());
        item.setMaterialCode(source.materialCode());
        item.setBrand(source.brand());
        item.setCategory(source.category());
        item.setMaterial(source.material());
        item.setSpec(source.spec());
        item.setLength(source.length());
        item.setUnit(source.unit());
        String warehouseName = warehouseSelectionSupport.normalizeWarehouseName(
                source.warehouseName() == null || source.warehouseName().isBlank() ? request.warehouseName() : source.warehouseName(),
                lineNo,
                true
        );
        item.setWarehouseName(warehouseName);
        item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), lineNo, true));
        item.setQuantity(source.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        sourceService.validateSourceSalesOrderItem(
                source,
                sourceSalesOrderItem,
                sourceSalesOrderItemId,
                request.customerName(),
                request.projectName(),
                warehouseName,
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
        item.setUnitPrice(source.unitPrice());
        BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
        item.setAmount(amount);
        return new LineApplyResult(sourceSalesOrderItemId, warehouseName, weightTon, amount);
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
