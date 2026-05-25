package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
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

    ItemMappingResult applyItemFields(
            PurchaseInbound inbound,
            PurchaseInboundItemRequest source,
            PurchaseInboundItem item,
            int lineNo,
            Material material,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            WeightSettlementResult weightSettlement,
            String headerWarehouseName,
            String headerSettlementMode) {

        item.setPurchaseInbound(inbound);
        item.setLineNo(lineNo);
        item.setMaterialCode(source.materialCode());
        item.setBrand(source.brand());
        item.setCategory(source.category());
        item.setMaterial(source.material());
        item.setSpec(source.spec());
        item.setLength(source.length());
        item.setUnit(source.unit());
        item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());

        String sourceOrderNo = resolveSourceOrderNo(source, sourcePurchaseOrderItemMap);
        String warehouseName = warehouseSelectionSupport.normalizeWarehouseName(
                source.warehouseName() == null || source.warehouseName().isBlank()
                        ? headerWarehouseName : source.warehouseName(),
                lineNo, true);
        item.setWarehouseName(warehouseName);

        String settlementMode = source.settlementMode() != null && !source.settlementMode().isBlank()
                ? source.settlementMode()
                : headerSettlementMode != null && !headerSettlementMode.isBlank()
                        ? headerSettlementMode : "现结";
        item.setSettlementMode(settlementMode);

        item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), lineNo, true));
        item.setQuantity(source.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setPieceWeightTon(weightSettlement.pieceWeightTon());
        item.setWeightTon(weightSettlement.weightTon());
        item.setWeighWeightTon(weightSettlement.weighWeightTon());
        item.setWeightAdjustmentTon(weightSettlement.weightAdjustmentTon());
        item.setWeightAdjustmentAmount(weightSettlement.weightAdjustmentAmount());
        item.setUnitPrice(source.unitPrice());
        item.setAmount(TradeItemCalculator.calculateAmount(weightSettlement.weightTon(), source.unitPrice()));

        return new ItemMappingResult(
                sourceOrderNo,
                warehouseName,
                weightSettlement.weightTon(),
                item.getAmount(),
                source.sourcePurchaseOrderItemId(),
                weightSettlement.weightAdjustmentTon(),
                weightSettlement.weighWeightTon(),
                source.quantity(),
                weightSettlement.calculatedWeightTon()
        );
    }

    private String resolveSourceOrderNo(PurchaseInboundItemRequest source,
                                         Map<Long, PurchaseOrderItem> sourceMap) {
        if (source.sourcePurchaseOrderItemId() == null) return null;
        PurchaseOrderItem sourceItem = sourceMap.get(source.sourcePurchaseOrderItemId());
        if (sourceItem == null || sourceItem.getPurchaseOrder() == null) return null;
        return sourceItem.getPurchaseOrder().getOrderNo();
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
