package com.leo.erp.purchase.inbound.service;

import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemResponse;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PurchaseInboundResponseAssembler {

    private static final String MODULE_KEY = "purchase-inbound";
    private static final String PAYABLE = "PAYABLE";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final ItemAllocationNativeRepository itemAllocationRepo;
    private final DocumentChargeItemService chargeItemService;

    public PurchaseInboundResponseAssembler(PurchaseInboundMapper purchaseInboundMapper,
                                            PurchaseInboundItemRepository purchaseInboundItemRepository,
                                            ItemAllocationNativeRepository itemAllocationRepo) {
        this(purchaseInboundMapper, purchaseInboundItemRepository, itemAllocationRepo, null);
    }

    @Autowired
    public PurchaseInboundResponseAssembler(PurchaseInboundMapper purchaseInboundMapper,
                                            PurchaseInboundItemRepository purchaseInboundItemRepository,
                                            ItemAllocationNativeRepository itemAllocationRepo,
                                            DocumentChargeItemService chargeItemService) {
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.itemAllocationRepo = itemAllocationRepo;
        this.chargeItemService = chargeItemService;
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
                response.supplierName(), response.settlementCompanyId(), response.settlementCompanyName(),
                response.warehouseName(), response.inboundDate(),
                response.settlementMode(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                TradeItemCalculator.scaleWeightTon(totalWeighWeightTon),
                TradeItemCalculator.scaleWeightTon(totalWeightAdjustmentTon),
                response.items(),
                response.chargeItems(),
                response.totalChargeAmount(),
                response.payableAmount()
        );
    }

    PurchaseInboundResponse toDetailResponse(PurchaseInbound inbound) {
        Map<Long, Integer> allocatedQuantityMap = loadAllocatedQuantityMap(inbound);
        PurchaseInboundResponse response = purchaseInboundMapper.toResponse(inbound);
        List<DocumentChargeItemResponse> chargeItems = loadChargeItems(inbound);
        BigDecimal totalChargeAmount = totalChargeAmount(chargeItems);
        List<PurchaseInboundItem> items = inbound.getItems();
        BigDecimal totalWeighWeightTon = items.stream()
                .map(i -> i.getWeighWeightTon() != null ? i.getWeighWeightTon() : i.getWeightTon())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWeightAdjustmentTon = items.stream()
                .map(i -> i.getWeightAdjustmentTon() != null ? i.getWeightAdjustmentTon() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PurchaseInboundItemResponse> itemResponses = items.stream().map(item -> new PurchaseInboundItemResponse(
                item.getId(), item.getLineNo(), item.getMaterialCode(),
                item.getBrand(), item.getCategory(), item.getMaterial(),
                item.getSpec(), item.getLength(), item.getUnit(),
                item.getSourcePurchaseOrderItemId(),
                item.getSettlementCompanyId(), item.getSettlementCompanyName(),
                item.getWarehouseName(), item.getSettlementMode(),
                item.getBatchNo(),
                remainingQuantity(item, allocatedQuantityMap),
                item.getQuantity(), item.getQuantityUnit(),
                item.getPieceWeightTon(), item.getPiecesPerBundle(),
                item.getWeightTon(), item.getWeighWeightTon(),
                item.getWeightAdjustmentTon(),
                item.getWeightAdjustmentAmount(),
                item.getUnitPrice(), item.getAmount()
        )).toList();
        return new PurchaseInboundResponse(
                response.id(), response.inboundNo(), response.purchaseOrderNo(),
                response.supplierName(), response.settlementCompanyId(), response.settlementCompanyName(),
                response.warehouseName(), response.inboundDate(),
                response.settlementMode(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                TradeItemCalculator.scaleWeightTon(totalWeighWeightTon),
                TradeItemCalculator.scaleWeightTon(totalWeightAdjustmentTon),
                itemResponses,
                chargeItems,
                totalChargeAmount,
                payableAmount(response.totalAmount(), totalChargeAmount)
        );
    }

    private List<DocumentChargeItemResponse> loadChargeItems(PurchaseInbound inbound) {
        if (chargeItemService == null || inbound.getId() == null) {
            return List.of();
        }
        List<DocumentChargeItemResponse> chargeItems = chargeItemService.listResponses(MODULE_KEY, inbound.getId());
        return chargeItems == null ? List.of() : chargeItems;
    }

    private BigDecimal totalChargeAmount(List<DocumentChargeItemResponse> chargeItems) {
        return chargeItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.billable()))
                .filter(item -> PAYABLE.equals(item.chargeDirection()))
                .map(DocumentChargeItemResponse::amount)
                .filter(amount -> amount != null)
                .map(this::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal payableAmount(BigDecimal totalAmount, BigDecimal totalChargeAmount) {
        return scaleAmount(totalAmount == null ? BigDecimal.ZERO : totalAmount).add(totalChargeAmount);
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO_AMOUNT;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
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
