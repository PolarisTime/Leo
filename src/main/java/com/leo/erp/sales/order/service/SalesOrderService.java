package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemPieceWeightService;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SalesOrderService extends AbstractCrudService<SalesOrder, SalesOrderRequest, SalesOrderResponse> {

    private final SalesOrderRepository repository;
    private final SalesOrderMapper salesOrderMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderMapper salesOrderMapper,
                             TradeItemMaterialSupport tradeItemMaterialSupport,
                             PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                             PurchaseOrderItemQueryService purchaseOrderItemQueryService,
                             PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService,
                             SalesOrderItemRepository salesOrderItemRepository,
                             WarehouseSelectionSupport warehouseSelectionSupport,
                             WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.salesOrderMapper = salesOrderMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.purchaseOrderItemPieceWeightService = purchaseOrderItemPieceWeightService;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query,
                                         String keyword,
                                         String customerName,
                                         String status,
                                         java.time.LocalDate startDate,
                                         java.time.LocalDate endDate) {
        Specification<SalesOrder> spec = Specs.<SalesOrder>notDeleted()
                .and(Specs.keywordLike(keyword, "orderNo", "purchaseOrderNo", "customerName", "projectName"))
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("deliveryDate", startDate, endDate));
        return page(query, spec, repository);
    }

    private static final String[] SALES_ORDER_SEARCH_FIELDS = {"orderNo", "purchaseOrderNo", "customerName", "projectName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, SALES_ORDER_SEARCH_FIELDS, maxSize, Specs.notDeleted(), repository);
    }

    @Override
    protected SalesOrderResponse toDetailResponse(SalesOrder entity) {
        SalesOrderResponse response = salesOrderMapper.toResponse(entity);
        return new SalesOrderResponse(
                response.id(), response.orderNo(), response.purchaseInboundNo(),
                response.purchaseOrderNo(), response.customerName(), response.projectName(), response.deliveryDate(),
                response.salesName(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                entity.getItems().stream().map(item -> new SalesOrderItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(), item.getSourceInboundItemId(), item.getSourcePurchaseOrderItemId(), item.getWarehouseName(), item.getBatchNo(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(),
                        item.getPiecesPerBundle(), item.getWeightTon(),
                        item.getUnitPrice(), item.getAmount()
                )).toList()
        );
    }

    @Override
    protected void validateCreate(SalesOrderRequest request) {
        if (repository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        }
    }

    @Override
    protected void validateUpdate(SalesOrder entity, SalesOrderRequest request) {
        if (!entity.getOrderNo().equals(request.orderNo()) && repository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售订单号已存在");
        }
    }

    @Override
    protected void beforeDelete(SalesOrder entity) {
        purchaseOrderItemPieceWeightService.releaseSalesOrderItems(
                entity.getItems().stream()
                        .map(SalesOrderItem::getId)
                        .filter(id -> id != null)
                        .toList()
        );
    }

    @Override
    protected SalesOrder newEntity() {
        return new SalesOrder();
    }

    @Override
    protected void assignId(SalesOrder entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesOrder> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售订单不存在";
    }

    @Override
    protected void apply(SalesOrder entity, SalesOrderRequest request) {
        String nextStatus = (request.status() == null || request.status().isBlank()) ? StatusConstants.DRAFT : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-orders",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED,
                StatusConstants.SALES_COMPLETED
        );
        entity.setOrderNo(request.orderNo());
        entity.setPurchaseInboundNo(request.purchaseInboundNo());
        entity.setPurchaseOrderNo(request.purchaseOrderNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setSalesName(request.salesName());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(SalesOrderItemRequest::materialCode).toList()
        );
        List<Long> sourceInboundItemIds = extractSourceInboundItemIds(request);
        List<Long> sourcePurchaseOrderItemIds = extractSourcePurchaseOrderItemIds(request);
        Map<Long, PurchaseInboundItem> sourceInboundItemMap = loadSourceInboundItemMap(sourceInboundItemIds);
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        Map<Long, SourceAllocation> inboundAllocatedMap = loadInboundAllocatedMap(sourceInboundItemIds, entity.getId());
        Map<Long, SourceAllocation> purchaseOrderAllocatedMap = loadPurchaseOrderAllocatedMap(sourcePurchaseOrderItemIds, entity.getId());
        Map<Long, SourceAllocation> requestInboundAllocatedMap = new HashMap<>();
        Map<Long, SourceAllocation> requestPurchaseOrderAllocatedMap = new HashMap<>();
        purchaseOrderItemPieceWeightService.releaseSalesOrderItems(
                entity.getItems().stream()
                        .map(SalesOrderItem::getId)
                        .filter(id -> id != null)
                        .toList()
        );
        List<SalesOrderItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SalesOrderItem::getId,
                SalesOrderItemRequest::id,
                SalesOrderItem::new,
                this::nextId,
                SalesOrderItem::setId
        );
        for (int i = 0; i < request.items().size(); i++) {
            SalesOrderItemRequest source = request.items().get(i);
            Material material = materialMap.get(source.materialCode());
            SalesOrderItem item = items.get(i);
            item.setSalesOrder(entity);
            item.setLineNo(i + 1);
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setSourceInboundItemId(source.sourceInboundItemId());
            item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
            item.setWarehouseName(warehouseSelectionSupport.normalizeWarehouseName(source.warehouseName(), i + 1, true));
            validateSourceAllocation(source, i + 1, sourceInboundItemMap, sourcePurchaseOrderItemMap,
                    inboundAllocatedMap, purchaseOrderAllocatedMap, requestInboundAllocatedMap, requestPurchaseOrderAllocatedMap);
            item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), i + 1, true));
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            BigDecimal pieceWeightTon = resolveSalesOrderPieceWeightTon(source, sourcePurchaseOrderItemMap);
            item.setPieceWeightTon(pieceWeightTon);
            item.setPiecesPerBundle(source.piecesPerBundle());
            BigDecimal weightTon = resolveSalesOrderWeightTon(source, sourceInboundItemMap, sourcePurchaseOrderItemMap,
                    inboundAllocatedMap, requestInboundAllocatedMap, item.getId(), i + 1, pieceWeightTon);
            item.setWeightTon(weightTon);
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
            item.setAmount(amount);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
            if (source.sourcePurchaseOrderItemId() != null) {
                requestPurchaseOrderAllocatedMap.merge(
                        source.sourcePurchaseOrderItemId(),
                        new SourceAllocation(source.quantity(), weightTon),
                        this::mergeSourceAllocation
                );
            } else if (source.sourceInboundItemId() != null) {
                requestInboundAllocatedMap.merge(
                        source.sourceInboundItemId(),
                        new SourceAllocation(source.quantity(), weightTon),
                        this::mergeSourceAllocation
                );
            }
        }
        entity.getItems().sort(java.util.Comparator.comparing(SalesOrderItem::getLineNo));
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    @Override
    protected SalesOrder saveEntity(SalesOrder entity) {
        return repository.save(entity);
    }

    @Override
    protected SalesOrderResponse toResponse(SalesOrder entity) {
        return salesOrderMapper.toResponse(entity);
    }

    private List<Long> extractSourceInboundItemIds(SalesOrderRequest request) {
        return request.items().stream()
                .map(SalesOrderItemRequest::sourceInboundItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private List<Long> extractSourcePurchaseOrderItemIds(SalesOrderRequest request) {
        return request.items().stream()
                .map(SalesOrderItemRequest::sourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private Map<Long, PurchaseInboundItem> loadSourceInboundItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        return purchaseInboundItemQueryService.findAllActiveByIdIn(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseInboundItem::getId, item -> item));
    }

    private Map<Long, PurchaseOrderItem> loadSourcePurchaseOrderItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        return purchaseOrderItemQueryService.findActiveByIdIn(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseOrderItem::getId, item -> item));
    }

    private Map<Long, SourceAllocation> loadInboundAllocatedMap(List<Long> sourceInboundItemIds, Long currentOrderId) {
        if (sourceInboundItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SourceAllocation> allocatedMap = new HashMap<>();
        salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(sourceInboundItemIds, currentOrderId)
                .forEach(summary -> allocatedMap.put(
                        summary.getSourceInboundItemId(),
                        new SourceAllocation(
                                Math.toIntExact(summary.getTotalQuantity()),
                                TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                        )
                ));
        return allocatedMap;
    }

    private Map<Long, SourceAllocation> loadPurchaseOrderAllocatedMap(List<Long> sourcePurchaseOrderItemIds, Long currentOrderId) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SourceAllocation> allocatedMap = new HashMap<>();
        salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(sourcePurchaseOrderItemIds, currentOrderId)
                .forEach(summary -> allocatedMap.put(
                        summary.getSourcePurchaseOrderItemId(),
                        new SourceAllocation(
                                Math.toIntExact(summary.getTotalQuantity()),
                                TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                        )
                ));
        return allocatedMap;
    }

    private BigDecimal resolveSalesOrderPieceWeightTon(
            SalesOrderItemRequest source,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        int sourceQuantity = sourcePurchaseOrderItem.getQuantity() == null ? 0 : sourcePurchaseOrderItem.getQuantity();
        BigDecimal sourceWeightTon = TradeItemCalculator.scaleWeightTon(sourcePurchaseOrderItem.getWeightTon());
        if (sourceQuantity <= 0 || sourceWeightTon.compareTo(BigDecimal.ZERO) <= 0) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        return TradeItemCalculator.calculateAveragePieceWeightTon(sourceQuantity, sourceWeightTon);
    }

    private BigDecimal resolveSalesOrderWeightTon(
            SalesOrderItemRequest source,
            Map<Long, PurchaseInboundItem> sourceInboundItemMap,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            Map<Long, SourceAllocation> inboundAllocatedMap,
            Map<Long, SourceAllocation> requestInboundAllocatedMap,
            Long salesOrderItemId,
            int lineNo,
            BigDecimal pieceWeightTon
    ) {
        BigDecimal defaultWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId != null) {
            return resolvePurchaseOrderWeightTon(
                    source,
                    sourcePurchaseOrderItemMap,
                    salesOrderItemId,
                    lineNo,
                    defaultWeightTon
            );
        }
        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null || source.quantity() == null || source.quantity() <= 0) {
            return defaultWeightTon;
        }
        PurchaseInboundItem sourceInboundItem = sourceInboundItemMap.get(sourceInboundItemId);
        if (sourceInboundItem == null || sourceInboundItem.getWeighWeightTon() == null) {
            return defaultWeightTon;
        }
        int sourceQuantity = sourceInboundItem.getQuantity() == null ? 0 : sourceInboundItem.getQuantity();
        if (sourceQuantity <= 0) {
            return defaultWeightTon;
        }
        SourceAllocation persistedAllocation = inboundAllocatedMap.getOrDefault(sourceInboundItemId, SourceAllocation.ZERO);
        SourceAllocation requestAllocation = requestInboundAllocatedMap.getOrDefault(sourceInboundItemId, SourceAllocation.ZERO);
        int allocatedQuantityAfterCurrent = persistedAllocation.quantity() + requestAllocation.quantity() + source.quantity();
        if (allocatedQuantityAfterCurrent < sourceQuantity) {
            return defaultWeightTon;
        }
        BigDecimal residualWeightTon = TradeItemCalculator.scaleWeightTon(
                sourceInboundItem.getWeighWeightTon()
                        .subtract(persistedAllocation.weightTon())
                        .subtract(requestAllocation.weightTon())
        );
        return residualWeightTon.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(3)
                : residualWeightTon;
    }

    private BigDecimal resolvePurchaseOrderWeightTon(
            SalesOrderItemRequest source,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            Long salesOrderItemId,
            int lineNo,
            BigDecimal defaultWeightTon
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null || source.quantity() == null || source.quantity() <= 0) {
            return defaultWeightTon;
        }
        PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null || sourcePurchaseOrderItem.getWeightTon() == null) {
            return defaultWeightTon;
        }
        int sourceQuantity = sourcePurchaseOrderItem.getQuantity() == null ? 0 : sourcePurchaseOrderItem.getQuantity();
        if (sourceQuantity <= 0) {
            return defaultWeightTon;
        }
        return purchaseOrderItemPieceWeightService.allocateForSalesOrderItem(
                sourcePurchaseOrderItem,
                source.quantity(),
                salesOrderItemId,
                lineNo
        );
    }

    private void validateSourceAllocation(
            SalesOrderItemRequest source,
            int lineNo,
            Map<Long, PurchaseInboundItem> sourceInboundItemMap,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            Map<Long, SourceAllocation> inboundAllocatedMap,
            Map<Long, SourceAllocation> purchaseOrderAllocatedMap,
            Map<Long, SourceAllocation> requestInboundAllocatedMap,
            Map<Long, SourceAllocation> requestPurchaseOrderAllocatedMap
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId != null) {
            PurchaseOrderItem sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
            if (sourcePurchaseOrderItem == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
            }
            int allocatedQuantity = purchaseOrderAllocatedMap.getOrDefault(sourcePurchaseOrderItemId, SourceAllocation.ZERO).quantity();
            int requestedQuantity = requestPurchaseOrderAllocatedMap.getOrDefault(sourcePurchaseOrderItemId, SourceAllocation.ZERO).quantity();
            validateAvailableQuantity(source.quantity(), sourcePurchaseOrderItem.getQuantity(), allocatedQuantity, requestedQuantity, lineNo);
            return;
        }

        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null) {
            return;
        }
        PurchaseInboundItem sourceInboundItem = sourceInboundItemMap.get(sourceInboundItemId);
        if (sourceInboundItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不存在");
        }
        int allocatedQuantity = inboundAllocatedMap.getOrDefault(sourceInboundItemId, SourceAllocation.ZERO).quantity();
        int requestedQuantity = requestInboundAllocatedMap.getOrDefault(sourceInboundItemId, SourceAllocation.ZERO).quantity();
        validateAvailableQuantity(source.quantity(), sourceInboundItem.getQuantity(), allocatedQuantity, requestedQuantity, lineNo);
    }

    private void validateAvailableQuantity(Integer requestedQuantityValue,
                                           Integer sourceQuantityValue,
                                           int allocatedQuantity,
                                           int requestedQuantity,
                                           int lineNo) {
        int sourceQuantity = sourceQuantityValue == null ? 0 : sourceQuantityValue;
        int currentQuantity = requestedQuantityValue == null ? 0 : requestedQuantityValue;
        int availableQuantity = sourceQuantity - allocatedQuantity;
        if (currentQuantity + requestedQuantity > availableQuantity) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行可关联数量不足，剩余可用 " + Math.max(availableQuantity - requestedQuantity, 0) + " 件"
            );
        }
    }

    private SourceAllocation mergeSourceAllocation(SourceAllocation left, SourceAllocation right) {
        return new SourceAllocation(
                left.quantity() + right.quantity(),
                TradeItemCalculator.scaleWeightTon(left.weightTon().add(right.weightTon()))
        );
    }

    private record SourceAllocation(
            int quantity,
            BigDecimal weightTon
    ) {

        private static final SourceAllocation ZERO = new SourceAllocation(0, BigDecimal.ZERO.setScale(3));
    }
}
