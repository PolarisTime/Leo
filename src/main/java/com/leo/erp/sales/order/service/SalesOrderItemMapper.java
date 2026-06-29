package com.leo.erp.sales.order.service;

import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 销售订单明细字段映射器，从 SalesOrderService.apply() 中提取以降低方法复杂度。
 */
@Component
public class SalesOrderItemMapper {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;

    public SalesOrderItemMapper(TradeItemMaterialSupport tradeItemMaterialSupport,
                                WarehouseSelectionSupport warehouseSelectionSupport) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
    }

    void applyItemFields(SalesOrder entity, SalesOrderItemRequest source, SalesOrderItem item,
                         int lineNo, String materialCode, TradeMaterialSnapshot material,
                         BigDecimal weightTon, BigDecimal pieceWeightTon) {
        item.setSalesOrder(entity);
        item.setLineNo(lineNo);
        item.setMaterialCode(materialCode);
        item.setBrand(source.brand());
        item.setCategory(source.category());
        item.setMaterial(source.material());
        item.setSpec(source.spec());
        item.setLength(source.length());
        item.setUnit(source.unit());
        item.setSourceInboundItemId(source.sourceInboundItemId());
        item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
        item.setWarehouseName(warehouseSelectionSupport.normalizeWarehouseName(source.warehouseName(), lineNo, true));
        item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), lineNo, true));
        item.setQuantity(source.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        item.setPieceWeightTon(pieceWeightTon);
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setWeightTon(weightTon);
        item.setUnitPrice(source.unitPrice());
    }
}
