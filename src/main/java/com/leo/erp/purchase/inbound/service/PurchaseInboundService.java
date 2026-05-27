package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.allocation.repository.ItemAllocationNativeRepository;
import com.leo.erp.purchase.inbound.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PurchaseInboundService extends AbstractCrudService<
        PurchaseInbound, PurchaseInboundRequest, PurchaseInboundResponse> {

    /**
     * Fulfillment tolerance: allow 5% over-receipt.
     * E.g., if expected=100, actual can be 95-105.
     */
    private static final BigDecimal FULFILLMENT_TOLERANCE = new BigDecimal("0.05");

    private final PurchaseInboundRepository repository;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final MaterialCategoryRepository materialCategoryRepository;
    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService;
    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final ItemAllocationNativeRepository itemAllocationRepo;
    private final InboundItemMapper inboundItemMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public PurchaseInboundService(PurchaseInboundRepository repository,
                                  SnowflakeIdGenerator idGenerator,
                                  PurchaseInboundMapper purchaseInboundMapper,
                                  TradeItemMaterialSupport tradeItemMaterialSupport,
                                  WarehouseSelectionSupport warehouseSelectionSupport,
                                  MaterialCategoryRepository materialCategoryRepository,
                                  PurchaseInboundItemRepository purchaseInboundItemRepository,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService,
                                  PurchaseOrderItemQueryService purchaseOrderItemQueryService,
                                  ItemAllocationNativeRepository itemAllocationRepo,
                                  InboundItemMapper inboundItemMapper,
                                  WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.materialCategoryRepository = materialCategoryRepository;
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemPieceWeightService = purchaseOrderItemPieceWeightService;
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.itemAllocationRepo = itemAllocationRepo;
        this.inboundItemMapper = inboundItemMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInboundResponse> page(PageQuery query, PageFilter filter) {
        Specification<PurchaseInbound> spec = Specs.<PurchaseInbound>keywordLike(
                        filter.keyword(),
                        "inboundNo", "purchaseOrderNo", "supplierName"
                )
                .and(Specs.equalIfPresent("supplierName", filter.name()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent(
                        "inboundDate", filter.startDate(), filter.endDate()
                ));
        return page(query, spec, repository);
    }

    private static final String[] INBOUND_SEARCH_FIELDS = {"inboundNo", "purchaseOrderNo", "supplierName"};

    @Transactional(readOnly = true)
    public java.util.List<PurchaseInboundResponse> search(String keyword, int maxSize) {
        return search(keyword, INBOUND_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected PurchaseInboundResponse toDetailResponse(PurchaseInbound inbound) {
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
                response.supplierName(), response.warehouseName(), response.inboundDate(),
                response.settlementMode(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                TradeItemCalculator.scaleWeightTon(totalWeighWeightTon),
                TradeItemCalculator.scaleWeightTon(totalWeightAdjustmentTon),
                items.stream().map(item -> new PurchaseInboundItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(),
                        item.getSourcePurchaseOrderItemId(),
                        item.getWarehouseName(), item.getSettlementMode(),
                        item.getBatchNo(),
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

    private List<Long> extractSourcePurchaseOrderItemIds(PurchaseInboundRequest request) {
        return request.items().stream()
                .map(PurchaseInboundItemRequest::sourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private Map<Long, PurchaseOrderItem> loadSourcePurchaseOrderItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PurchaseOrderItem> sourceMap = purchaseOrderItemQueryService.findActiveByIdIn(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseOrderItem::getId, item -> item));

        // Pre-load lazy purchaseOrder to avoid LazyInitializationException in InboundItemMapper
        sourceMap.values().forEach(item -> {
            if (item.getPurchaseOrder() != null) {
                item.getPurchaseOrder().getOrderNo();
            }
        });
        return sourceMap;
    }

    private Map<Long, Integer> loadAllocatedQuantityMap(List<Long> sourcePurchaseOrderItemIds, Long currentInboundId) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> allocatedQuantityMap = new HashMap<>();
        purchaseInboundItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIdsExcludingInbound(
                        sourcePurchaseOrderItemIds,
                        currentInboundId
                )
                .forEach(summary -> allocatedQuantityMap.put(
                        summary.getSourcePurchaseOrderItemId(),
                        Math.toIntExact(summary.getTotalQuantity())
                ));
        return allocatedQuantityMap;
    }

    private Set<String> loadPurchaseWeighRequiredCategoryNames(PurchaseInboundRequest request) {
        List<String> categoryNames = request.items().stream()
                .map(PurchaseInboundItemRequest::category)
                .map(this::normalizeCategoryName)
                .filter(category -> !category.isBlank())
                .distinct()
                .toList();
        if (categoryNames.isEmpty()) {
            return Set.of();
        }
        return materialCategoryRepository.findByCategoryNameInAndDeletedFlagFalse(categoryNames).stream()
                .filter(category -> Boolean.TRUE.equals(category.getPurchaseWeighRequired()))
                .map(MaterialCategory::getCategoryName)
                .map(this::normalizeCategoryName)
                .collect(Collectors.toSet());
    }

    private String normalizeCategoryName(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isPurchaseWeighSettlement(String settlementMode) {
        return "过磅".equals(settlementMode == null ? "" : settlementMode.trim());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveLineSettlementMode(
            PurchaseInboundItemRequest source,
            PurchaseInboundRequest request,
            int lineNo
    ) {
        String settlementMode = trimToNull(source.settlementMode());
        if (settlementMode == null) {
            settlementMode = trimToNull(request.settlementMode());
        }
        if (settlementMode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行请选择结算方式");
        }
        return settlementMode;
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

    private BigDecimal requireWeighWeightTon(PurchaseInboundItemRequest source, int lineNo) {
        BigDecimal weighWeightTon = source.weighWeightTon();
        if (weighWeightTon == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行需填写过磅重量");
        }
        BigDecimal normalized = TradeItemCalculator.scaleWeightTon(weighWeightTon);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行过磅重量不能小于0");
        }
        if (source.quantity() != null && source.quantity() > 0 && normalized.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行过磅重量必须大于0");
        }
        return normalized;
    }

    private WeightSettlementResult resolveWeightSettlement(
            PurchaseInboundItemRequest source,
            int lineNo,
            Set<String> purchaseWeighRequiredCategoryNames,
            String settlementMode
    ) {
        BigDecimal sourcePieceWeightTon = TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        BigDecimal baseWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), sourcePieceWeightTon);
        boolean purchaseWeighRequired = purchaseWeighRequiredCategoryNames
                .contains(normalizeCategoryName(source.category()));
        boolean purchaseWeighSettlement = isPurchaseWeighSettlement(settlementMode);
        if (!purchaseWeighRequired && !purchaseWeighSettlement) {
            return new WeightSettlementResult(
                    baseWeightTon,
                    null,
                    BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE),
                    BigDecimal.ZERO.setScale(PrecisionConstants.AMOUNT_SCALE),
                    sourcePieceWeightTon,
                    baseWeightTon
            );
        }
        if (!purchaseWeighSettlement) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行商品类别需按过磅入库，请将本行结算方式改为过磅");
        }
        BigDecimal weighWeightTon = requireWeighWeightTon(source, lineNo);
        BigDecimal theoreticalWeightTon = resolveAdjustmentBaseWeightTon(source);
        BigDecimal averagePieceWeightTon = TradeItemCalculator
                .calculateAveragePieceWeightTon(source.quantity(), weighWeightTon);
        BigDecimal weightAdjustmentTon = TradeItemCalculator
                .scaleWeightTon(weighWeightTon.subtract(theoreticalWeightTon));
        BigDecimal weightAdjustmentAmount = TradeItemCalculator
                .calculateAmount(weightAdjustmentTon, source.unitPrice());
        return new WeightSettlementResult(
                theoreticalWeightTon, weighWeightTon,
                weightAdjustmentTon, weightAdjustmentAmount,
                averagePieceWeightTon, weighWeightTon
        );
    }

    private BigDecimal resolveAdjustmentBaseWeightTon(PurchaseInboundItemRequest source) {
        return TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
    }

    private Map<Long, BigDecimal> loadPersistedWeightAdjustmentMap(
            List<Long> sourcePurchaseOrderItemIds, Long currentInboundId
    ) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> adjustmentMap = new HashMap<>();
        purchaseInboundItemRepository.summarizeWeightAdjustmentBySourcePurchaseOrderItemIdsExcludingInbound(
                        sourcePurchaseOrderItemIds,
                        currentInboundId
                )
                .forEach(summary -> adjustmentMap.put(
                        summary.getSourcePurchaseOrderItemId(),
                        TradeItemCalculator.scaleWeightTon(summary.getTotalWeightAdjustmentTon())
                ));
        return adjustmentMap;
    }

    private Map<Long, SourceWeighAccumulator> loadPersistedWeighAccumulatorMap(
            List<Long> sourcePurchaseOrderItemIds, Long currentInboundId
    ) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SourceWeighAccumulator> weighAccumulatorMap = new HashMap<>();
        purchaseInboundItemRepository.summarizeWeighWeightBySourcePurchaseOrderItemIdsExcludingInbound(
                        sourcePurchaseOrderItemIds,
                        currentInboundId
                )
                .forEach(summary -> weighAccumulatorMap.put(
                        summary.getSourcePurchaseOrderItemId(),
                        new SourceWeighAccumulator(
                                Math.toIntExact(summary.getTotalQuantity()),
                                TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                        )
                ));
        return weighAccumulatorMap;
    }

    private Map<Long, BigDecimal> buildFallbackSourcePieceWeightMap(List<PurchaseInboundItem> items) {
        Map<Long, BigDecimal> sourcePieceWeightMap = new HashMap<>();
        for (PurchaseInboundItem item : items) {
            Long sourcePurchaseOrderItemId = item.getSourcePurchaseOrderItemId();
            if (sourcePurchaseOrderItemId == null || sourcePieceWeightMap.containsKey(sourcePurchaseOrderItemId)) {
                continue;
            }
            sourcePieceWeightMap.put(sourcePurchaseOrderItemId, resolveOriginalPieceWeightTon(item));
        }
        return sourcePieceWeightMap;
    }

    private BigDecimal resolveOriginalPieceWeightTon(PurchaseInboundItem item) {
        if (item.getQuantity() != null && item.getQuantity() > 0 && item.getWeightAdjustmentTon() != null) {
            BigDecimal originalWeightTon = TradeItemCalculator.scaleWeightTon(
                    TradeItemCalculator.safeBigDecimal(item.getWeightTon()).subtract(item.getWeightAdjustmentTon())
            );
            return TradeItemCalculator.calculateAveragePieceWeightTon(item.getQuantity(), originalWeightTon);
        }
        return TradeItemCalculator.scaleWeightTon(item.getPieceWeightTon());
    }

    private void writeBackPurchaseOrderWeights(
            List<Long> sourcePurchaseOrderItemIds,
            Long currentInboundId,
            Map<Long, BigDecimal> currentAdjustmentMap,
            Map<Long, SourceWeighAccumulator> currentWeighAccumulatorMap,
            Map<Long, BigDecimal> fallbackSourcePieceWeightMap,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap
    ) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> persistedAdjustmentMap = loadPersistedWeightAdjustmentMap(
                sourcePurchaseOrderItemIds, currentInboundId
        );
        Map<Long, SourceWeighAccumulator> persistedWeighAccumulatorMap = loadPersistedWeighAccumulatorMap(
                sourcePurchaseOrderItemIds, currentInboundId
        );
        Map<Long, PurchaseOrder> affectedOrderMap = loadAffectedPurchaseOrderMap(sourcePurchaseOrderItemMap);
        Map<Long, PurchaseOrderItem> writeBackItemMap = affectedOrderMap.values().stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item, (left, right) -> left));
        for (Long sourcePurchaseOrderItemId : sourcePurchaseOrderItemIds) {
            PurchaseOrderItem sourceItem = writeBackItemMap.get(sourcePurchaseOrderItemId);
            if (sourceItem == null) {
                continue;
            }
            SourceWeighAccumulator weighAccumulator = mergeWeighAccumulator(
                    persistedWeighAccumulatorMap.get(sourcePurchaseOrderItemId),
                    currentWeighAccumulatorMap.get(sourcePurchaseOrderItemId)
            );
            if (weighAccumulator != null && weighAccumulator.hasQuantity()) {
                BigDecimal averagePieceWeightTon = weighAccumulator.averagePieceWeightTon();
                BigDecimal actualWeightTon = weighAccumulator.isFullyAllocated(sourceItem.getQuantity())
                        ? TradeItemCalculator.scaleWeightTon(weighAccumulator.weightTon())
                        : TradeItemCalculator.calculateWeightTon(sourceItem.getQuantity(), averagePieceWeightTon);
                sourceItem.setWeightTon(actualWeightTon);
                sourceItem.setAmount(TradeItemCalculator.calculateAmount(actualWeightTon, sourceItem.getUnitPrice()));
                sourceItem.setActualWeightTon(actualWeightTon);
                sourceItem.setActualPieceWeightTon(TradeItemCalculator.scaleWeightTon(averagePieceWeightTon));
            } else {
                sourceItem.setActualWeightTon(null);
                sourceItem.setActualPieceWeightTon(null);
            }
        }
        affectedOrderMap.values().forEach(this::refreshPurchaseOrderTotals);
        if (!affectedOrderMap.isEmpty()) {
            purchaseOrderRepository.saveAll(affectedOrderMap.values());
            purchaseOrderItemPieceWeightService.regenerateForPurchaseOrderItems(
                    sourcePurchaseOrderItemIds.stream()
                            .map(writeBackItemMap::get)
                            .filter(item -> item != null)
                            .toList()
            );
        }
    }

    private Map<Long, PurchaseOrder> loadAffectedPurchaseOrderMap(
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap
    ) {
        List<Long> orderIds = sourcePurchaseOrderItemMap.values().stream()
                .map(PurchaseOrderItem::getPurchaseOrder)
                .filter(order -> order != null)
                .map(PurchaseOrder::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        // Order ids come from PurchaseOrderItemQueryService, which already checks source order access.
        return purchaseOrderRepository.findByIdInAndDeletedFlagFalse(orderIds).stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, order -> order));
    }

    private void maybeCompletePurchaseOrder(PurchaseOrder purchaseOrder) {
        if (!StatusConstants.AUDITED.equals(purchaseOrder.getStatus())) {
            return;
        }
        List<PurchaseInbound> allInbounds = repository
                .findByPurchaseOrderNoAndDeletedFlagFalse(purchaseOrder.getOrderNo());
        boolean allInboundCompleted = allInbounds.stream()
                .allMatch(i -> StatusConstants.INBOUND_COMPLETED.equals(i.getStatus()));
        if (!allInboundCompleted) {
            return;
        }

        // Pre-compute: aggregate received quantities by purchase order item ID
        Map<Long, Integer> receivedQtyByItemId = allInbounds.stream()
                .flatMap(inbound -> inbound.getItems().stream())
                .filter(item -> item.getSourcePurchaseOrderItemId() != null)
                .collect(Collectors.groupingBy(
                        PurchaseInboundItem::getSourcePurchaseOrderItemId,
                        Collectors.summingInt(
                                item -> item.getQuantity() != null ? item.getQuantity() : 0
                        )
                ));

        // Check each order item against pre-computed map with tolerance
        boolean allFulfilled = purchaseOrder.getItems().stream().allMatch(item -> {
            int expected = item.getQuantity() != null ? item.getQuantity() : 0;
            int actual = receivedQtyByItemId.getOrDefault(item.getId(), 0);

            // Zero-quantity items: must match exactly
            if (expected == 0) {
                return actual == 0;
            }

            // Calculate fulfillment ratio with tolerance
            BigDecimal ratio = BigDecimal.valueOf(actual)
                    .divide(BigDecimal.valueOf(expected), 4, RoundingMode.HALF_UP);
            BigDecimal lowerBound = BigDecimal.ONE.subtract(FULFILLMENT_TOLERANCE);
            BigDecimal upperBound = BigDecimal.ONE.add(FULFILLMENT_TOLERANCE);

            return ratio.compareTo(lowerBound) >= 0
                    && ratio.compareTo(upperBound) <= 0;
        });

        if (allFulfilled) {
            purchaseOrder.setStatus(StatusConstants.PURCHASE_COMPLETED);
        }
    }

    private void refreshPurchaseOrderTotals(PurchaseOrder purchaseOrder) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            totalWeight = totalWeight.add(TradeItemCalculator.safeBigDecimal(item.getWeightTon()));
            totalAmount = totalAmount.add(TradeItemCalculator.safeBigDecimal(item.getAmount()));
        }
        purchaseOrder.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        purchaseOrder.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private record SourceWeighAccumulator(
            int quantity,
            BigDecimal weightTon
    ) {

        boolean hasQuantity() {
            return quantity > 0;
        }

        boolean isFullyAllocated(Integer sourceQuantity) {
            return sourceQuantity != null && sourceQuantity > 0 && quantity >= sourceQuantity;
        }

        BigDecimal averagePieceWeightTon() {
            return TradeItemCalculator.calculateAveragePieceWeightTon(quantity, weightTon);
        }
    }

    private SourceWeighAccumulator mergeWeighAccumulator(SourceWeighAccumulator left, SourceWeighAccumulator right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return new SourceWeighAccumulator(
                left.quantity() + right.quantity(),
                TradeItemCalculator.scaleWeightTon(left.weightTon().add(right.weightTon()))
        );
    }

    private void validateSourcePurchaseOrderAllocation(
            PurchaseInboundItemRequest source,
            int lineNo,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            Map<Long, Integer> allocatedQuantityMap,
            Map<Long, Integer> requestAllocatedQuantityMap
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            return;
        }
        PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
        }
        int allocatedQuantity = allocatedQuantityMap.getOrDefault(sourcePurchaseOrderItemId, 0);
        int requestedQuantity = requestAllocatedQuantityMap.getOrDefault(sourcePurchaseOrderItemId, 0);
        int availableQuantity = sourcePurchaseOrderItem.getQuantity() - allocatedQuantity;
        if (source.quantity() + requestedQuantity > availableQuantity) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行可关联数量不足，剩余可用 " + Math.max(availableQuantity - requestedQuantity, 0) + " 件"
            );
        }
        requestAllocatedQuantityMap.merge(sourcePurchaseOrderItemId, source.quantity(), Integer::sum);
    }

    @Override
    protected void validateCreate(PurchaseInboundRequest request) {
        if (repository.existsByInboundNoAndDeletedFlagFalse(request.inboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库单号已存在");
        }
    }

    @Override
    protected void validateUpdate(PurchaseInbound inbound, PurchaseInboundRequest request) {
        boolean noChanged = !inbound.getInboundNo().equals(request.inboundNo());
        boolean noExists = repository.existsByInboundNoAndDeletedFlagFalse(
                request.inboundNo()
        );
        if (noChanged && noExists) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库单号已存在");
        }
    }

    @Override
    protected PurchaseInboundRequest normalizeCreateRequest(PurchaseInboundRequest request, long entityId) {
        return new PurchaseInboundRequest(
                resolveCreateBusinessNo("purchase-inbound", request.inboundNo(), entityId),
                request.purchaseOrderNo(),
                request.supplierName(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected PurchaseInboundRequest normalizeUpdateRequest(PurchaseInbound entity, PurchaseInboundRequest request) {
        return new PurchaseInboundRequest(
                entity.getInboundNo(),
                request.purchaseOrderNo(),
                request.supplierName(),
                request.warehouseName(),
                request.inboundDate(),
                request.settlementMode(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected PurchaseInbound newEntity() {
        return new PurchaseInbound();
    }

    @Override
    protected void assignId(PurchaseInbound entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<PurchaseInbound> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<PurchaseInbound> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "采购入库不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected void apply(PurchaseInbound inbound, PurchaseInboundRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "采购入库状态",
                StatusConstants.ALLOWED_PURCHASE_INBOUND_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-inbound",
                inbound.getStatus(),
                nextStatus,
                StatusConstants.AUDITED,
                StatusConstants.INBOUND_COMPLETED
        );
        inbound.setInboundNo(request.inboundNo());
        inbound.setPurchaseOrderNo(request.purchaseOrderNo());
        inbound.setSupplierName(request.supplierName());
        inbound.setInboundDate(request.inboundDate());
        inbound.setStatus(nextStatus);
        inbound.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(PurchaseInboundItemRequest::materialCode).toList()
        );
        List<Long> previousSourcePurchaseOrderItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Long> sourcePurchaseOrderItemIds = extractSourcePurchaseOrderItemIds(request);
        List<Long> affectedSourcePurchaseOrderItemIds = Stream
                .concat(previousSourcePurchaseOrderItemIds.stream(), sourcePurchaseOrderItemIds.stream())
                .distinct()
                .toList();
        Map<Long, BigDecimal> fallbackSourcePieceWeightMap = buildFallbackSourcePieceWeightMap(inbound.getItems());
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap =
                loadSourcePurchaseOrderItemMap(affectedSourcePurchaseOrderItemIds);
        Map<Long, Integer> allocatedQuantityMap = loadAllocatedQuantityMap(sourcePurchaseOrderItemIds, inbound.getId());
        Map<Long, Integer> requestAllocatedQuantityMap = new HashMap<>();
        Set<String> purchaseWeighRequiredCategoryNames = loadPurchaseWeighRequiredCategoryNames(request);
        Map<Long, BigDecimal> currentAdjustmentMap = new HashMap<>();
        Map<Long, SourceWeighAccumulator> currentWeighAccumulatorMap = new HashMap<>();
        LinkedHashSet<String> sourcePurchaseOrderNos = new LinkedHashSet<>();
        String firstLineWarehouseName = null;
        List<PurchaseInboundItem> items = ManagedEntityItemSupport.syncById(
                inbound.getItems(),
                request.items(),
                PurchaseInboundItem::getId,
                PurchaseInboundItemRequest::id,
                PurchaseInboundItem::new,
                this::nextId,
                PurchaseInboundItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            PurchaseInboundItemRequest source = request.items().get(i);
            Material material = materialMap.get(source.materialCode());
            PurchaseInboundItem item = items.get(i);
            int lineNo = i + 1;

            validateSourcePurchaseOrderAllocation(
                    source, lineNo, sourcePurchaseOrderItemMap,
                    allocatedQuantityMap, requestAllocatedQuantityMap
            );
            WeightSettlementResult weightSettlement = resolveWeightSettlement(
                    source, lineNo, purchaseWeighRequiredCategoryNames,
                    resolveLineSettlementMode(source, request, lineNo));

            var result = inboundItemMapper.applyItemFields(
                    inbound, source, item, lineNo, material,
                    sourcePurchaseOrderItemMap,
                    new InboundItemMapper.ItemMappingContext(weightSettlement, request.warehouseName(), request.settlementMode()));

            if (result.sourceOrderNo() != null) sourcePurchaseOrderNos.add(result.sourceOrderNo());
            if (firstLineWarehouseName == null) firstLineWarehouseName = result.firstLineWarehouseName();

            totalWeight = totalWeight.add(result.weightTon());
            totalAmount = totalAmount.add(result.amount());

            if (result.sourcePurchaseOrderItemId() != null) {
                currentAdjustmentMap.merge(
                        result.sourcePurchaseOrderItemId(),
                        result.weightAdjustmentTon(),
                        BigDecimal::add
                );
                if (result.weighWeightTon() != null) {
                    currentWeighAccumulatorMap.merge(
                            result.sourcePurchaseOrderItemId(),
                            new SourceWeighAccumulator(result.quantity(), result.sourceWeightTon()),
                            this::mergeWeighAccumulator);
                }
            }
        }
        inbound.getItems().sort(java.util.Comparator.comparing(PurchaseInboundItem::getLineNo));
        inbound.setPurchaseOrderNo(sourcePurchaseOrderNos.isEmpty()
                ? request.purchaseOrderNo()
                : String.join(", ", sourcePurchaseOrderNos));
        inbound.setWarehouseName(resolveHeaderWarehouseName(request.warehouseName(), firstLineWarehouseName));
        inbound.setSettlementMode(resolveHeaderSettlementMode(request.settlementMode(), items));
        inbound.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        inbound.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
        writeBackPurchaseOrderWeights(
                affectedSourcePurchaseOrderItemIds,
                inbound.getId(),
                currentAdjustmentMap,
                currentWeighAccumulatorMap,
                fallbackSourcePieceWeightMap,
                sourcePurchaseOrderItemMap
        );
        if (StatusConstants.INBOUND_COMPLETED.equals(nextStatus)) {
            sourcePurchaseOrderItemMap.values().stream()
                    .map(PurchaseOrderItem::getPurchaseOrder)
                    .filter(order -> order != null)
                    .distinct()
                    .forEach(this::maybeCompletePurchaseOrder);
        }
    }

    @Override
    protected void beforeDelete(PurchaseInbound inbound) {
        List<Long> sourcePurchaseOrderItemIds = inbound.getItems().stream()
                .map(PurchaseInboundItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, BigDecimal> fallbackSourcePieceWeightMap = buildFallbackSourcePieceWeightMap(inbound.getItems());
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap =
                loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        writeBackPurchaseOrderWeights(
                sourcePurchaseOrderItemIds,
                inbound.getId(),
                Map.of(),
                Map.of(),
                fallbackSourcePieceWeightMap,
                sourcePurchaseOrderItemMap
        );
    }

    @Override
    protected PurchaseInbound saveEntity(PurchaseInbound entity) {
        return repository.save(entity);
    }

    @Override
    protected PurchaseInboundResponse toResponse(PurchaseInbound entity) {
        return purchaseInboundMapper.toResponse(entity);
    }

    @Override
    protected PurchaseInboundResponse toSavedResponse(PurchaseInbound entity) {
        return toDetailResponse(entity);
    }
}
