package com.leo.erp.purchase.order.service;

import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class PurchaseOrderApplyService {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;

    public PurchaseOrderApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                     WarehouseSelectionSupport warehouseSelectionSupport,
                                     PurchaseInboundItemQueryService purchaseInboundItemQueryService) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
    }

    void applyItems(PurchaseOrder purchaseOrder,
                    PurchaseOrderRequest request,
                    LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        // Keep newly created items detached until every required field is initialized.
        List<PurchaseOrderItem> items = ManagedEntityItemSupport.syncById(
                new ArrayList<>(purchaseOrder.getItems()),
                request.items(),
                PurchaseOrderItem::getId,
                PurchaseOrderItemRequest::id,
                PurchaseOrderItem::new,
                nextIdSupplier,
                PurchaseOrderItem::setId
        );
        Map<Long, BigDecimal> inboundWeightAdjustmentMap =
                purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(
                        items.stream().map(PurchaseOrderItem::getId).toList()
                );

        for (int index = 0; index < request.items().size(); index++) {
            PurchaseOrderItemRequest itemRequest = request.items().get(index);
            PurchaseOrderItem item = items.get(index);
            int lineNo = index + 1;
            TradeMaterialSnapshot material = tradeItemMaterialSupport.resolveMaterial(
                    itemRequest.materialId(),
                    itemRequest.materialCode(),
                    lineNo
            );
            BigDecimal weightTon = applyItem(
                    purchaseOrder,
                    item,
                    itemRequest,
                    material,
                    inboundWeightAdjustmentMap.getOrDefault(item.getId(), BigDecimal.ZERO),
                    lineNo
            );
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, itemRequest.unitPrice());
            item.setAmount(amount);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }

        purchaseOrder.getItems().clear();
        purchaseOrder.getItems().addAll(items);
        purchaseOrder.getItems().sort(java.util.Comparator.comparing(PurchaseOrderItem::getLineNo));
        purchaseOrder.setTotalWeight(totalWeight);
        purchaseOrder.setTotalAmount(totalAmount);
    }

    private BigDecimal applyItem(PurchaseOrder purchaseOrder,
                                 PurchaseOrderItem item,
                                 PurchaseOrderItemRequest itemRequest,
                                 TradeMaterialSnapshot material,
                                 BigDecimal weightAdjustmentTon,
                                 int lineNo) {
        item.setPurchaseOrder(purchaseOrder);
        item.setLineNo(lineNo);
        item.setMaterialId(material.materialId());
        item.setMaterialCode(material.materialCode());
        item.setBrand(itemRequest.brand());
        item.setCategory(itemRequest.category());
        item.setMaterial(itemRequest.material());
        item.setSpec(itemRequest.spec());
        item.setLength(itemRequest.length());
        item.setUnit(itemRequest.unit());
        var warehouse = warehouseSelectionSupport.resolveWarehouse(
                itemRequest.warehouseId(),
                itemRequest.warehouseName(),
                lineNo,
                true
        );
        item.setWarehouseId(warehouse.warehouseId());
        item.setWarehouseName(warehouse.warehouseName());
        item.setBatchNo(tradeItemMaterialSupport.normalizeRequiredBatchNo(itemRequest.batchNo(), lineNo));
        item.setQuantity(itemRequest.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(itemRequest.quantityUnit()));
        item.setPieceWeightTon(itemRequest.pieceWeightTon());
        item.setPiecesPerBundle(itemRequest.piecesPerBundle());
        BigDecimal weightTon = resolveWeightTon(itemRequest, weightAdjustmentTon);
        item.setWeightTon(weightTon);
        item.setUnitPrice(itemRequest.unitPrice());
        return weightTon;
    }

    private BigDecimal resolveWeightTon(PurchaseOrderItemRequest itemRequest, BigDecimal weightAdjustmentTon) {
        BigDecimal baseWeightTon = TradeItemCalculator.calculateWeightTon(itemRequest.quantity(), itemRequest.pieceWeightTon());
        BigDecimal requestedWeightTon = itemRequest.weightTon() == null
                ? null
                : TradeItemCalculator.scaleWeightTon(itemRequest.weightTon());
        return requestedWeightTon != null
                && weightAdjustmentTon.compareTo(BigDecimal.ZERO) != 0
                && requestedWeightTon.compareTo(baseWeightTon) == 0
                ? requestedWeightTon
                : TradeItemCalculator.scaleWeightTon(baseWeightTon.add(weightAdjustmentTon));
    }
}
