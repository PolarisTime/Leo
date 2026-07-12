package com.leo.erp.purchase.inbound.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemResponse;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundResponseAssembler {

    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final ItemAllocationNativeRepository itemAllocationRepo;

    public PurchaseInboundResponseAssembler(PurchaseInboundMapper purchaseInboundMapper,
                                            PurchaseInboundItemRepository purchaseInboundItemRepository,
                                            ItemAllocationNativeRepository itemAllocationRepo) {
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.itemAllocationRepo = itemAllocationRepo;
    }

    Map<Long, PurchaseInboundItemRepository.InboundWeightSummary> loadInboundWeightSummaryMap(List<PurchaseInbound> inbounds) {
        List<Long> inboundIds = inbounds.stream()
                .map(PurchaseInbound::getId)
                .distinct()
                .toList();
        return loadInboundWeightSummaryMapByIds(inboundIds);
    }

    Map<Long, PurchaseInboundItemRepository.InboundWeightSummary> loadInboundWeightSummaryMapByIds(List<Long> inboundIds) {
        if (inboundIds.isEmpty()) {
            return Map.of();
        }
        return purchaseInboundItemRepository.summarizeWeightByInboundIds(inboundIds).stream()
                .collect(Collectors.toMap(
                        PurchaseInboundItemRepository.InboundWeightSummary::getInboundId,
                        summary -> summary
                ));
    }

    PurchaseInboundResponse toListResponse(
            PurchaseInbound inbound,
            PurchaseInboundItemRepository.InboundWeightSummary weightSummary
    ) {
        return withInboundWeightSummary(purchaseInboundMapper.toResponse(inbound), weightSummary);
    }

    PurchaseInboundResponse withInboundWeightSummary(
            PurchaseInboundResponse response,
            PurchaseInboundItemRepository.InboundWeightSummary weightSummary
    ) {
        BigDecimal totalWeighWeightTon = weightSummary == null
                ? response.totalWeight()
                : weightSummary.getTotalWeighWeightTon();
        BigDecimal totalWeightAdjustmentTon = weightSummary == null
                ? BigDecimal.ZERO
                : weightSummary.getTotalWeightAdjustmentTon();
        return new PurchaseInboundResponse(
                response.id(), response.inboundNo(), response.purchaseOrderNo(),
                response.supplierId(),
                response.supplierCode(),
                response.supplierName(), response.settlementCompanyId(), response.settlementCompanyName(),
                response.warehouseId(),
                response.warehouseName(), response.inboundDate(),
                response.settlementMode(), response.totalWeight(), response.totalAmount(),
                response.status(), response.deletedFlag(), response.remark(),
                TradeItemCalculator.scaleWeightTon(totalWeighWeightTon),
                TradeItemCalculator.scaleWeightTon(totalWeightAdjustmentTon),
                response.items()
        );
    }

    PurchaseInboundResponse toDetailResponse(PurchaseInbound inbound) {
        Map<Long, Integer> allocatedQuantityMap = loadAllocatedQuantityMap(inbound);
        PurchaseInboundResponse response = purchaseInboundMapper.toResponse(inbound);
        List<PurchaseInboundItem> items = inbound.getItems();
        BigDecimal totalWeighWeightTon = items.stream()
                .map(i -> i.getWeighWeightTon() != null ? i.getWeighWeightTon() : i.getWeightTon())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWeightAdjustmentTon = items.stream()
                .map(i -> i.getWeightAdjustmentTon() != null ? i.getWeightAdjustmentTon() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseInboundResponse(
                response.id(), response.inboundNo(), response.purchaseOrderNo(),
                response.supplierId(),
                response.supplierCode(),
                response.supplierName(), response.settlementCompanyId(), response.settlementCompanyName(),
                response.warehouseId(),
                response.warehouseName(), response.inboundDate(),
                response.settlementMode(), response.totalWeight(), response.totalAmount(),
                response.status(), response.deletedFlag(), response.remark(),
                TradeItemCalculator.scaleWeightTon(totalWeighWeightTon),
                TradeItemCalculator.scaleWeightTon(totalWeightAdjustmentTon),
                items.stream().map(item -> new PurchaseInboundItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialId(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(),
                        item.getSourcePurchaseOrderItemId(),
                        item.getSettlementCompanyId(), item.getSettlementCompanyName(),
                        item.getWarehouseId(),
                        item.getWarehouseName(), item.getSettlementMode(),
                        item.getBatchNo(),
                        item.getBatchNoNormalized(),
                        remainingQuantity(item, allocatedQuantityMap),
                        item.getQuantity(), item.getQuantityUnit(),
                        item.getPieceWeightTon(), item.getPiecesPerBundle(),
                        item.getWeightTon(), item.getWeighWeightTon(),
                        item.getWeightAdjustmentTon(),
                        item.getWeightAdjustmentAmount(),
                        item.getUnitPrice(), item.getAmount()
                )).toList()
        );
    }

    private Map<Long, Integer> loadAllocatedQuantityMap(PurchaseInbound inbound) {
        List<Long> inboundItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getId)
                .distinct()
                .toList();
        if (inboundItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> allocatedMap = new HashMap<>();
        itemAllocationRepo.summarizeSalesByInboundItems(inboundItemIds, null)
                .forEach(p -> allocatedMap.put(p.getSourceItemId(), Math.toIntExact(p.getTotalQuantity())));
        return allocatedMap;
    }

    private Integer remainingQuantity(PurchaseInboundItem item, Map<Long, Integer> allocatedQuantityMap) {
        int allocatedQuantity = allocatedQuantityMap.getOrDefault(item.getId(), 0);
        return Math.max(0, item.getQuantity() - allocatedQuantity);
    }
}
