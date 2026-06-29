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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class PurchaseInboundApplyService {

    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final PurchaseInboundSourceValidator sourceValidator;
    private final PurchaseInboundWeightSettlementService weightSettlementService;
    private final PurchaseInboundWeightWriteBackService weightWriteBackService;
    private final InboundItemMapper inboundItemMapper;

    public PurchaseInboundApplyService(TradeItemMaterialSupport tradeItemMaterialSupport,
                                       PurchaseInboundSourceValidator sourceValidator,
                                       PurchaseInboundWeightSettlementService weightSettlementService,
                                       PurchaseInboundWeightWriteBackService weightWriteBackService,
                                       InboundItemMapper inboundItemMapper) {
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.sourceValidator = sourceValidator;
        this.weightSettlementService = weightSettlementService;
        this.weightWriteBackService = weightWriteBackService;
        this.inboundItemMapper = inboundItemMapper;
    }

    void applyItems(PurchaseInbound inbound, PurchaseInboundRequest request, LongSupplier nextIdSupplier) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<String, TradeMaterialSnapshot> materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(PurchaseInboundItemRequest::materialCode).toList()
        );
        List<Long> previousSourcePurchaseOrderItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        PurchaseInboundSourceValidator.SourceValidationContext sourceContext =
                sourceValidator.prepareContext(request, inbound.getId(), previousSourcePurchaseOrderItemIds);
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = sourceContext.sourcePurchaseOrderItemMap();
        Map<String, PurchaseInboundWeightSettlementService.PurchaseWeighCategoryRule> purchaseWeighCategoryRules =
                weightSettlementService.loadPurchaseWeighCategoryRules(request);
        Map<Long, PurchaseInboundWeightWriteBackService.SourceWeighAccumulator> currentWeighAccumulatorMap =
                new HashMap<>();
        LinkedHashSet<String> sourcePurchaseOrderNos = new LinkedHashSet<>();
        String firstLineWarehouseName = null;
        List<PurchaseInboundItem> items = ManagedEntityItemSupport.syncById(
                inbound.getItems(),
                request.items(),
                PurchaseInboundItem::getId,
                PurchaseInboundItemRequest::id,
                PurchaseInboundItem::new,
                nextIdSupplier,
                PurchaseInboundItem::setId
        );

        for (int i = 0; i < request.items().size(); i++) {
            PurchaseInboundItemRequest source = request.items().get(i);
            TradeMaterialSnapshot material = materialMap.get(source.materialCode());
            PurchaseInboundItem item = items.get(i);
            int lineNo = i + 1;

            sourceValidator.validateLine(source, lineNo, request, sourceContext);
            WeightSettlementResult weightSettlement = weightSettlementService.resolveWeightSettlement(
                    source, lineNo, purchaseWeighCategoryRules,
                    weightSettlementService.resolveLineSettlementMode(source, request, lineNo));

            InboundItemMapper.ItemMappingResult result = inboundItemMapper.applyItemFields(
                    inbound, source, item, lineNo, material,
                    sourcePurchaseOrderItemMap,
                    new InboundItemMapper.ItemMappingContext(
                            weightSettlement,
                            request.warehouseName(),
                            request.settlementMode()
                    ));

            if (result.sourceOrderNo() != null) {
                sourcePurchaseOrderNos.add(result.sourceOrderNo());
            }
            if (firstLineWarehouseName == null) {
                firstLineWarehouseName = result.firstLineWarehouseName();
            }

            totalWeight = totalWeight.add(result.weightTon());
            totalAmount = totalAmount.add(result.amount());

            collectCurrentWeighAccumulator(currentWeighAccumulatorMap, result);
        }
        inbound.getItems().sort(Comparator.comparing(PurchaseInboundItem::getLineNo));
        inbound.setPurchaseOrderNo(sourcePurchaseOrderNos.isEmpty()
                ? request.purchaseOrderNo()
                : String.join(", ", sourcePurchaseOrderNos));
        inbound.setWarehouseName(resolveHeaderWarehouseName(request.warehouseName(), firstLineWarehouseName));
        inbound.setSettlementMode(resolveHeaderSettlementMode(request.settlementMode(), items));
        applyHeaderSettlementCompany(inbound, items);
        inbound.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        inbound.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
        weightWriteBackService.writeBackPurchaseOrderWeights(
                sourceContext.affectedSourcePurchaseOrderItemIds(),
                inbound.getId(),
                currentWeighAccumulatorMap,
                sourcePurchaseOrderItemMap
        );
    }

    private void collectCurrentWeighAccumulator(
            Map<Long, PurchaseInboundWeightWriteBackService.SourceWeighAccumulator> currentWeighAccumulatorMap,
            InboundItemMapper.ItemMappingResult result
    ) {
        if (result.sourcePurchaseOrderItemId() == null || result.weighWeightTon() == null) {
            return;
        }
        currentWeighAccumulatorMap.merge(
                result.sourcePurchaseOrderItemId(),
                new PurchaseInboundWeightWriteBackService.SourceWeighAccumulator(
                        result.quantity(),
                        result.sourceWeightTon()
                ),
                weightWriteBackService::mergeWeighAccumulator
        );
    }

    private String resolveHeaderWarehouseName(String requestWarehouseName, String firstLineWarehouseName) {
        String normalized = trimToNull(requestWarehouseName);
        return normalized == null ? firstLineWarehouseName : normalized;
    }

    private String resolveHeaderSettlementMode(String requestSettlementMode, List<PurchaseInboundItem> items) {
        String normalized = trimToNull(requestSettlementMode);
        if (normalized != null) {
            return normalized;
        }
        List<String> lineSettlementModes = items.stream()
                .map(PurchaseInboundItem::getSettlementMode)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .toList();
        if (lineSettlementModes.isEmpty()) {
            return "理算";
        }
        return lineSettlementModes.size() == 1 ? lineSettlementModes.get(0) : "混合";
    }

    private void applyHeaderSettlementCompany(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        List<SettlementCompanySnapshot> snapshots = items.stream()
                .map(item -> new SettlementCompanySnapshot(item.getSettlementCompanyId(), trimToNull(item.getSettlementCompanyName())))
                .filter(snapshot -> snapshot.id() != null || snapshot.name() != null)
                .distinct()
                .toList();
        if (snapshots.isEmpty()) {
            inbound.setSettlementCompanyId(null);
            inbound.setSettlementCompanyName(null);
            return;
        }
        if (snapshots.size() == 1) {
            SettlementCompanySnapshot snapshot = snapshots.get(0);
            inbound.setSettlementCompanyId(snapshot.id());
            inbound.setSettlementCompanyName(snapshot.name());
            return;
        }
        inbound.setSettlementCompanyId(null);
        inbound.setSettlementCompanyName("多结算主体");
    }

    private String trimToNull(String value) {
        return BusinessDocumentValidator.trimToNull(value);
    }

    private record SettlementCompanySnapshot(Long id, String name) {
    }
}
