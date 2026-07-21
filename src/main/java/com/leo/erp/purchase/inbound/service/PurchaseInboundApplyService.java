package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

@Service
public class PurchaseInboundApplyService {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final PurchaseInboundSourceValidator sourceValidator;
    private final PurchaseInboundWeightSettlementService weightSettlementService;
    private final InboundItemMapper inboundItemMapper;

    public PurchaseInboundApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                       PurchaseInboundSourceValidator sourceValidator,
                                       PurchaseInboundWeightSettlementService weightSettlementService,
                                       InboundItemMapper inboundItemMapper) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.sourceValidator = sourceValidator;
        this.weightSettlementService = weightSettlementService;
        this.inboundItemMapper = inboundItemMapper;
    }

    void applyItems(PurchaseInbound inbound, PurchaseInboundRequest request, LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Long> previousSourcePurchaseOrderItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        PurchaseInboundSourceValidator.SourceValidationContext sourceContext =
                sourceValidator.prepareContext(request, inbound.getId(), previousSourcePurchaseOrderItemIds);
        inbound.setAffectedSourcePurchaseOrderItemIds(sourceContext.affectedSourcePurchaseOrderItemIds());
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = sourceContext.sourcePurchaseOrderItemMap();
        Map<String, PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule> purchaseWeighCategoryRules =
                weightSettlementService.loadPurchaseWeighCategoryRules(request);
        LinkedHashSet<String> sourcePurchaseOrderNos = new LinkedHashSet<>();
        String firstLineWarehouseName = null;
        Long firstLineWarehouseId = null;
        LinkedHashSet<Long> warehouseIds = new LinkedHashSet<>();
        List<PurchaseInboundItem> managedItems = inbound.getItems();
        List<PurchaseInboundItem> items = ManagedEntityItemSupport.syncById(
                new ArrayList<>(managedItems),
                request.items(),
                PurchaseInboundItem::getId,
                PurchaseInboundItemRequest::id,
                PurchaseInboundItem::new,
                nextIdSupplier,
                PurchaseInboundItem::setId
        );

        for (int i = 0; i < request.items().size(); i++) {
            PurchaseInboundItemRequest source = request.items().get(i);
            int lineNo = i + 1;
            PurchaseInboundItem item = items.get(i);
            PurchaseOrderItem sourceOrderItem = source.sourcePurchaseOrderItemId() == null
                    ? null
                    : sourcePurchaseOrderItemMap.get(source.sourcePurchaseOrderItemId());
            Long materialId = source.materialId() != null
                    ? source.materialId()
                    : sourceOrderItem == null ? null : sourceOrderItem.getMaterialId();
            TradeMaterialSnapshot material = tradeItemMaterialSupport.resolveMaterial(
                    materialId,
                    source.materialCode(),
                    lineNo
            );

            sourceValidator.validateLine(source, lineNo, request, sourceContext);
            String lineSettlementMode = weightSettlementService.resolveLineSettlementMode(source, request, lineNo);
            WeightSettlementResult weightSettlement = weightSettlementService.resolveWeightSettlement(
                    source, lineNo, purchaseWeighCategoryRules,
                    lineSettlementMode,
                    com.leo.erp.common.support.StatusConstants.DRAFT.equals(inbound.getStatus()));

            InboundItemMapper.ItemMappingResult result = inboundItemMapper.applyItemFields(
                    inbound, source, item, lineNo, material.materialCode(), material,
                    sourcePurchaseOrderItemMap,
                    new InboundItemMapper.ItemMappingContext(
                            weightSettlement,
                            request.warehouseId(),
                            request.warehouseName(),
                            request.settlementMode()
                    ));

            if (result.sourceOrderNo() != null) {
                sourcePurchaseOrderNos.add(result.sourceOrderNo());
            }
            if (firstLineWarehouseName == null) {
                firstLineWarehouseName = result.firstLineWarehouseName();
            }
            if (firstLineWarehouseId == null) {
                firstLineWarehouseId = item.getWarehouseId();
            }
            if (item.getWarehouseId() != null) {
                warehouseIds.add(item.getWarehouseId());
            }

            totalWeight = totalWeight.add(result.weightTon());
            totalAmount = totalAmount.add(result.amount());

        }
        managedItems.clear();
        managedItems.addAll(items);
        managedItems.sort(Comparator.comparing(PurchaseInboundItem::getLineNo));
        inbound.setPurchaseOrderNo(sourcePurchaseOrderNos.isEmpty()
                ? request.purchaseOrderNo()
                : String.join(", ", sourcePurchaseOrderNos));
        applyHeaderSupplier(inbound, request, sourcePurchaseOrderItemMap);
        if (warehouseIds.size() > 1) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "采购入库单明细存在不同仓库，不能合并保存"
            );
        }
        inbound.setWarehouseId(request.warehouseId() == null ? firstLineWarehouseId : request.warehouseId());
        inbound.setWarehouseName(resolveHeaderWarehouseName(request.warehouseName(), firstLineWarehouseName));
        inbound.setSettlementMode(resolveHeaderSettlementMode(request.settlementMode(), items));
        applyHeaderSettlementCompany(inbound, items);
        inbound.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        inbound.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private void applyHeaderSupplier(PurchaseInbound inbound,
                                     PurchaseInboundRequest request,
                                     Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap) {
        LinkedHashSet<String> supplierCodes = new LinkedHashSet<>();
        LinkedHashSet<String> supplierNames = new LinkedHashSet<>();
        LinkedHashSet<Long> supplierIds = new LinkedHashSet<>();
        request.items().stream()
                .map(PurchaseInboundItemRequest::sourcePurchaseOrderItemId)
                .filter(Objects::nonNull)
                .map(sourcePurchaseOrderItemMap::get)
                .filter(Objects::nonNull)
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(Objects::nonNull)
                .forEach(order -> {
                    if (order.getSupplierId() != null) {
                        supplierIds.add(order.getSupplierId());
                    }
                    String supplierCode = trimToNull(order.getSupplierCode());
                    String supplierName = trimToNull(order.getSupplierName());
                    if (supplierCode != null) {
                        supplierCodes.add(supplierCode);
                    }
                    if (supplierName != null) {
                        supplierNames.add(supplierName);
                    }
                });
        if (supplierIds.size() > 1 || supplierCodes.size() > 1 || supplierNames.size() > 1) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "来源采购订单存在不同供应商，不能合并生成采购入库单"
            );
        }
        String supplierCode = supplierCodes.isEmpty()
                ? trimToNull(request.supplierCode())
                : supplierCodes.iterator().next();
        String supplierName = supplierNames.isEmpty()
                ? trimToNull(request.supplierName())
                : supplierNames.iterator().next();
        inbound.setSupplierId(supplierIds.isEmpty() ? request.supplierId() : supplierIds.iterator().next());
        if (supplierCode != null) {
            inbound.setSupplierCode(supplierCode);
        }
        if (supplierName != null) {
            inbound.setSupplierName(supplierName);
        }
    }

    List<Long> sourcePurchaseOrderIds(PurchaseInbound inbound) {
        List<Long> sourceItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sourceItemIds.isEmpty()) {
            return List.of();
        }
        return sourceValidator.loadSourcePurchaseOrderItemMap(sourceItemIds).values().stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(Objects::nonNull)
                .map(com.leo.erp.purchase.order.domain.entity.PurchaseOrder::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String resolveHeaderWarehouseName(String requestWarehouseName, String firstLineWarehouseName) {
        String normalized = trimToNull(requestWarehouseName);
        return normalized == null ? firstLineWarehouseName : normalized;
    }

    private String resolveHeaderSettlementMode(String requestSettlementMode, List<PurchaseInboundItem> items) {
        List<String> lineSettlementModes = items.stream()
                .map(PurchaseInboundItem::getSettlementMode)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .toList();
        if (lineSettlementModes.isEmpty()) {
            String normalized = trimToNull(requestSettlementMode);
            return normalized == null ? "理算" : normalized;
        }
        return lineSettlementModes.size() == 1 ? lineSettlementModes.get(0) : "混合";
    }

    private void applyHeaderSettlementCompany(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        List<SettlementCompanySnapshot> snapshots = items.stream()
                .map(item -> new SettlementCompanySnapshot(item.getSettlementCompanyId(), trimToNull(item.getSettlementCompanyName())))
                .distinct()
                .toList();
        if (snapshots.size() > 1) {
            throw new com.leo.erp.common.error.BusinessException(
                    com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                    "来源采购订单存在不同结算主体，不能合并生成采购入库单"
            );
        }
        if (snapshots.isEmpty()
                || snapshots.getFirst().id() == null && snapshots.getFirst().name() == null) {
            inbound.setSettlementCompanyId(null);
            inbound.setSettlementCompanyName(null);
            return;
        }
        SettlementCompanySnapshot snapshot = snapshots.getFirst();
        inbound.setSettlementCompanyId(snapshot.id());
        inbound.setSettlementCompanyName(snapshot.name());
    }

    private String trimToNull(String value) {
        return BusinessDocumentValidator.trimToNull(value);
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }
}
