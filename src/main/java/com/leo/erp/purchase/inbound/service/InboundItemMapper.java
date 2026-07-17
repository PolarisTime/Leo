package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采购入库明细字段映射器，从 PurchaseInboundService.apply() 中提取以降低方法复杂度。
 */
@Component
public class InboundItemMapper {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;

    public InboundItemMapper(TradeItemMaterialSupport tradeItemMaterialSupport,
                             WarehouseSelectionSupport warehouseSelectionSupport) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
    }

    /**
     * 明细映射上下文，封装头部级信息以减少方法参数数量。
     */
    record ItemMappingContext(
            WeightSettlementResult weightSettlement,
            Long headerWarehouseId,
            String headerWarehouseName,
            String headerSettlementMode
    ) {
        ItemMappingContext(WeightSettlementResult weightSettlement,
                           String headerWarehouseName,
                           String headerSettlementMode) {
            this(weightSettlement, null, headerWarehouseName, headerSettlementMode);
        }
    }

    ItemMappingResult applyItemFields(
            PurchaseInbound inbound,
            PurchaseInboundItemRequest source,
            PurchaseInboundItem item,
            int lineNo,
            String materialCode,
            TradeMaterialSnapshot material,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            ItemMappingContext ctx) {

        item.setPurchaseInbound(inbound);
        item.setLineNo(lineNo);
        PurchaseOrderItem sourceItem = source.sourcePurchaseOrderItemId() == null
                ? null
                : sourcePurchaseOrderItemMap.get(source.sourcePurchaseOrderItemId());
        item.setMaterialId(sourceItem != null && sourceItem.getMaterialId() != null
                ? sourceItem.getMaterialId() : material.materialId());
        item.setMaterialCode(sourceItem != null && sourceItem.getMaterialCode() != null
                ? sourceItem.getMaterialCode() : materialCode);
        item.setBrand(source.brand());
        item.setCategory(source.category());
        item.setMaterial(source.material());
        item.setSpec(source.spec());
        item.setLength(source.length());
        item.setUnit(source.unit());
        item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
        applySettlementCompany(item, source.sourcePurchaseOrderItemId(), sourcePurchaseOrderItemMap);

        String sourceOrderNo = resolveSourceOrderNo(source, sourcePurchaseOrderItemMap);
        Long requestedWarehouseId = source.warehouseId() == null ? ctx.headerWarehouseId() : source.warehouseId();
        String requestedWarehouseName = source.warehouseName() == null || source.warehouseName().isBlank()
                ? ctx.headerWarehouseName() : source.warehouseName();
        WarehouseSnapshot warehouse = sourceItem != null && sourceItem.getWarehouseId() != null
                ? new WarehouseSnapshot(
                        sourceItem.getWarehouseId(),
                        null,
                        sourceItem.getWarehouseName()
                )
                : warehouseSelectionSupport.resolveWarehouse(
                        requestedWarehouseId,
                        requestedWarehouseName,
                        lineNo,
                        true
                );
        item.setWarehouseId(warehouse.warehouseId());
        item.setWarehouseName(warehouse.warehouseName());

        String settlementMode = source.settlementMode() != null && !source.settlementMode().isBlank()
                ? source.settlementMode()
                : ctx.headerSettlementMode() != null && !ctx.headerSettlementMode().isBlank()
                        ? ctx.headerSettlementMode() : "现结";
        item.setSettlementMode(settlementMode);

        item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(source.batchNo(), lineNo));
        item.setQuantity(source.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setPieceWeightTon(ctx.weightSettlement().pieceWeightTon());
        item.setWeightTon(ctx.weightSettlement().weightTon());
        item.setWeighWeightTon(ctx.weightSettlement().weighWeightTon());
        item.setWeightAdjustmentTon(ctx.weightSettlement().weightAdjustmentTon());
        item.setWeightAdjustmentAmount(ctx.weightSettlement().weightAdjustmentAmount());
        item.setUnitPrice(source.unitPrice());
        item.setAmount(TradeItemCalculator.calculateAmount(ctx.weightSettlement().weightTon(), source.unitPrice()));
        clearToleranceConfirmation(item);

        return new ItemMappingResult(
                sourceOrderNo,
                warehouse.warehouseName(),
                ctx.weightSettlement().weightTon(),
                item.getAmount(),
                source.sourcePurchaseOrderItemId(),
                ctx.weightSettlement().weightAdjustmentTon(),
                ctx.weightSettlement().weighWeightTon(),
                source.quantity(),
                ctx.weightSettlement().calculatedWeightTon()
        );
    }

    private void clearToleranceConfirmation(PurchaseInboundItem item) {
        item.setToleranceDirection(null);
        item.setToleranceLimitPercent(null);
        item.setToleranceActualPercent(null);
        item.setToleranceReasonCode(null);
        item.setToleranceRemark(null);
        item.setToleranceConfirmedBy(null);
        item.setToleranceConfirmedName(null);
        item.setToleranceConfirmedAt(null);
    }

    private String resolveSourceOrderNo(PurchaseInboundItemRequest source,
                                         Map<Long, PurchaseOrderItem> sourceMap) {
        if (source.sourcePurchaseOrderItemId() == null) return null;
        PurchaseOrderItem sourceItem = sourceMap.get(source.sourcePurchaseOrderItemId());
        if (sourceItem == null || sourceItem.getPurchaseOrder() == null) return null;
        return sourceItem.getPurchaseOrder().getOrderNo();
    }

    private void applySettlementCompany(PurchaseInboundItem item,
                                        Long sourcePurchaseOrderItemId,
                                        Map<Long, PurchaseOrderItem> sourceMap) {
        if (sourcePurchaseOrderItemId == null) {
            item.setSettlementCompanyId(null);
            item.setSettlementCompanyName(null);
            return;
        }
        PurchaseOrderItem sourceItem = sourceMap.get(sourcePurchaseOrderItemId);
        if (sourceItem == null || sourceItem.getPurchaseOrder() == null) {
            item.setSettlementCompanyId(null);
            item.setSettlementCompanyName(null);
            return;
        }
        item.setSettlementCompanyId(sourceItem.getPurchaseOrder().getSettlementCompanyId());
        item.setSettlementCompanyName(sourceItem.getPurchaseOrder().getSettlementCompanyName());
    }

    record ItemMappingResult(
            String sourceOrderNo,
            String firstLineWarehouseName,
            BigDecimal weightTon,
            BigDecimal amount,
            Long sourcePurchaseOrderItemId,
            BigDecimal weightAdjustmentTon,
            BigDecimal weighWeightTon,
            Integer quantity,
            BigDecimal sourceWeightTon
    ) {}
}
