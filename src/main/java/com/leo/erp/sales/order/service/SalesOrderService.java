package com.leo.erp.sales.order.service;

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
import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord;
import com.leo.erp.master.material.domain.entity.Material;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SalesOrderService extends AbstractCrudService<SalesOrder, SalesOrderRequest, SalesOrderResponse> {

    private final SalesOrderRepository repository;
    private final SalesOrderMapper salesOrderMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final PurchaseItemQueryAppService purchaseItemQueryAppService;
    private final PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final SalesOrderItemMapper salesOrderItemMapper;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderMapper salesOrderMapper,
                             TradeItemMaterialSupport tradeItemMaterialSupport,
                             PurchaseItemQueryAppService purchaseItemQueryAppService,
                             PurchaseItemPieceWeightAppService purchaseItemPieceWeightAppService,
                             SalesOrderItemRepository salesOrderItemRepository,
                             WarehouseSelectionSupport warehouseSelectionSupport,
                             SalesOrderItemMapper salesOrderItemMapper,
                             WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.salesOrderMapper = salesOrderMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.purchaseItemQueryAppService = purchaseItemQueryAppService;
        this.purchaseItemPieceWeightAppService = purchaseItemPieceWeightAppService;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.salesOrderItemMapper = salesOrderItemMapper;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesOrder> spec = Specs.<SalesOrder>keywordLike(filter.keyword(), "orderNo", "purchaseOrderNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", filter.name()))
                .and(Specs.equalIfPresent("projectName", filter.projectName()))
                .and(Specs.equalIfPresent("status", filter.status()))
                .and(Specs.betweenIfPresent("deliveryDate", filter.startDate(), filter.endDate()));
        return page(query, spec, repository);
    }

    private static final String[] SALES_ORDER_SEARCH_FIELDS = {"orderNo", "purchaseOrderNo", "customerName", "projectName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, SALES_ORDER_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected SalesOrderResponse toDetailResponse(SalesOrder entity) {
        SalesOrderResponse response = salesOrderMapper.toResponse(entity);
        return new SalesOrderResponse(
                response.id(), response.orderNo(), response.purchaseInboundNo(),
                response.purchaseOrderNo(), response.customerCode(), response.customerName(), response.projectId(), response.projectName(), response.deliveryDate(),
                response.salesName(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                entity.getItems().stream().map(item -> new SalesOrderItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(), item.getSourceInboundItemId(), item.getSourcePurchaseOrderItemId(), item.getWarehouseName(), item.getBatchNo(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(),
                        item.getPiecesPerBundle(), item.getWeightTon(),
                        item.getUnitPrice(), item.getAmount(),
                        item.getOriginalWeightTon()
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
    protected SalesOrderRequest normalizeCreateRequest(SalesOrderRequest request, long entityId) {
        return new SalesOrderRequest(
                resolveCreateBusinessNo("sales-order", request.orderNo(), entityId),
                request.purchaseInboundNo(),
                request.purchaseOrderNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.deliveryDate(),
                request.salesName(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesOrderRequest normalizeUpdateRequest(SalesOrder entity, SalesOrderRequest request) {
        return new SalesOrderRequest(
                entity.getOrderNo(),
                request.purchaseInboundNo(),
                request.purchaseOrderNo(),
                request.customerCode(),
                request.customerName(),
                request.projectId(),
                request.projectName(),
                request.deliveryDate(),
                request.salesName(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected void beforeDelete(SalesOrder entity) {
        purchaseItemPieceWeightAppService.releaseSalesOrderItems(
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
    protected Optional<SalesOrder> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售订单不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected java.util.Set<String> allowedStatusTransitions() {
        return StatusConstants.DRAFT_AUDIT_TRANSITIONS;
    }

    @Override
    protected boolean allowProtectedStatusUpdate(SalesOrder entity, SalesOrderRequest request) {
        if (!StatusConstants.AUDITED.equals(normalize(entity.getStatus()))
                || !StatusConstants.DRAFT.equals(normalize(request.status()))) {
            return false;
        }
        return matchesStatusOnlyUpdate(entity, request);
    }

    @Override
    protected void apply(SalesOrder entity, SalesOrderRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                entity.getStatus() != null ? entity.getStatus() : StatusConstants.DRAFT,
                "销售订单状态",
                StatusConstants.ALLOWED_SALES_ORDER_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-order",
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
        entity.setCustomerCode(request.customerCode());
        entity.setProjectId(request.projectId());
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
        Map<Long, SourceInboundItemRecord> sourceInboundItemMap = loadSourceInboundItemMap(sourceInboundItemIds);
        Map<Long, SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        Map<Long, SourceAllocation> inboundAllocatedMap = loadInboundAllocatedMap(sourceInboundItemIds, entity.getId());
        Map<Long, SourceAllocation> purchaseOrderAllocatedMap = loadPurchaseOrderAllocatedMap(sourcePurchaseOrderItemIds, entity.getId());
        Map<Long, SourceAllocation> requestInboundAllocatedMap = new HashMap<>();
        Map<Long, SourceAllocation> requestPurchaseOrderAllocatedMap = new HashMap<>();
        LinkedHashSet<String> sourceInboundNos = new LinkedHashSet<>();
        LinkedHashSet<String> sourcePurchaseOrderNos = new LinkedHashSet<>();
        purchaseItemPieceWeightAppService.releaseSalesOrderItems(
                entity.getItems().stream()
                        .map(SalesOrderItem::getId)
                        .filter(id -> id != null)
                        .toList()
        );
        Map<Long, BigDecimal> purchaseOrderRemainingWeightMap = loadPurchaseOrderRemainingWeightMap(sourcePurchaseOrderItemIds);
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
            int lineNo = i + 1;
            SalesOrderItemRequest source = request.items().get(i);
            Material material = materialMap.get(source.materialCode());
            SalesOrderItem item = items.get(i);
            SourceInboundItemRecord sourceInboundItem = resolveSourceInbound(source, sourceInboundItemMap, sourceInboundNos, sourcePurchaseOrderNos);
            SourcePurchaseOrderItemRecord sourcePurchaseOrderItem = resolveSourcePurchaseOrder(source, sourcePurchaseOrderItemMap, sourcePurchaseOrderNos);
            validateSourceAllocation(source, lineNo, sourceInboundItemMap, sourcePurchaseOrderItemMap,
                    inboundAllocatedMap, purchaseOrderAllocatedMap, requestInboundAllocatedMap, requestPurchaseOrderAllocatedMap);
            BigDecimal pieceWeightTon = resolveSalesOrderPieceWeightTon(source, sourcePurchaseOrderItemMap,
                    purchaseOrderAllocatedMap, requestPurchaseOrderAllocatedMap, purchaseOrderRemainingWeightMap);
            BigDecimal weightTon = resolveSalesOrderWeightTon(source, sourceInboundItemMap,
                    inboundAllocatedMap, requestInboundAllocatedMap, pieceWeightTon);
            salesOrderItemMapper.applyItemFields(entity, source, item, lineNo, material, weightTon, pieceWeightTon);
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
        entity.setPurchaseInboundNo(sourceInboundNos.isEmpty()
                ? request.purchaseInboundNo()
                : String.join(", ", sourceInboundNos));
        entity.setPurchaseOrderNo(sourcePurchaseOrderNos.isEmpty()
                ? request.purchaseOrderNo()
                : String.join(", ", sourcePurchaseOrderNos));
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    @Override
    protected SalesOrder saveEntity(SalesOrder entity) {
        if (!hasPurchaseOrderBackedItems(entity)) {
            return repository.save(entity);
        }
        SalesOrder saved = repository.saveAndFlush(entity);
        finalizePurchaseOrderAllocations(saved);
        return repository.save(saved);
    }

    @Override
    protected SalesOrder saveStatusEntity(SalesOrder entity) {
        return repository.save(entity);
    }

    @Override
    protected SalesOrderResponse toResponse(SalesOrder entity) {
        return salesOrderMapper.toResponse(entity);
    }

    @Override
    protected SalesOrderResponse toSavedResponse(SalesOrder entity) {
        return toDetailResponse(entity);
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

    private Map<Long, SourceInboundItemRecord> loadSourceInboundItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        return purchaseItemQueryAppService.findSourceInboundItemsByIds(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(SourceInboundItemRecord::id, item -> item));
    }

    private Map<Long, SourcePurchaseOrderItemRecord> loadSourcePurchaseOrderItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        return purchaseItemQueryAppService.findSourcePurchaseOrderItemsByIds(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(SourcePurchaseOrderItemRecord::id, item -> item));
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

    private Map<Long, BigDecimal> loadPurchaseOrderRemainingWeightMap(List<Long> sourcePurchaseOrderItemIds) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> remainingWeightMap =
                purchaseItemPieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(sourcePurchaseOrderItemIds);
        return remainingWeightMap == null ? Map.of() : remainingWeightMap;
    }

    private SourceInboundItemRecord resolveSourceInbound(SalesOrderItemRequest source,
            Map<Long, SourceInboundItemRecord> sourceInboundItemMap,
            java.util.Set<String> sourceInboundNos,
            java.util.Set<String> sourcePurchaseOrderNos) {
        SourceInboundItemRecord si = source.sourceInboundItemId() == null ? null
                : sourceInboundItemMap.get(source.sourceInboundItemId());
        if (si != null && si.inboundNo() != null) {
            sourceInboundNos.add(si.inboundNo());
            if (si.purchaseOrderNo() != null) sourcePurchaseOrderNos.add(si.purchaseOrderNo());
        }
        return si;
    }

    private SourcePurchaseOrderItemRecord resolveSourcePurchaseOrder(SalesOrderItemRequest source,
            Map<Long, SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap,
            java.util.Set<String> sourcePurchaseOrderNos) {
        SourcePurchaseOrderItemRecord spo = source.sourcePurchaseOrderItemId() == null ? null
                : sourcePurchaseOrderItemMap.get(source.sourcePurchaseOrderItemId());
        if (spo != null && spo.orderNo() != null) sourcePurchaseOrderNos.add(spo.orderNo());
        return spo;
    }

    private BigDecimal resolveSalesOrderPieceWeightTon(
            SalesOrderItemRequest source,
            Map<Long, SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap,
            Map<Long, SourceAllocation> purchaseOrderAllocatedMap,
            Map<Long, SourceAllocation> requestPurchaseOrderAllocatedMap,
            Map<Long, BigDecimal> purchaseOrderRemainingWeightMap
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        SourcePurchaseOrderItemRecord sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        int sourceQuantity = sourcePurchaseOrderItem.quantity() == null ? 0 : sourcePurchaseOrderItem.quantity();
        BigDecimal sourceWeightTon = TradeItemCalculator.scaleWeightTon(sourcePurchaseOrderItem.weightTon());
        if (sourceQuantity <= 0 || sourceWeightTon.compareTo(BigDecimal.ZERO) <= 0) {
            return TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        }
        BigDecimal defaultPieceWeightTon = TradeItemCalculator.calculateAveragePieceWeightTon(sourceQuantity, sourceWeightTon);
        BigDecimal remainingWeightTon = purchaseOrderRemainingWeightMap.get(sourcePurchaseOrderItemId);
        if (remainingWeightTon == null || source.quantity() == null || source.quantity() <= 0) {
            return defaultPieceWeightTon;
        }
        SourceAllocation persistedAllocation = purchaseOrderAllocatedMap.getOrDefault(sourcePurchaseOrderItemId, SourceAllocation.ZERO);
        SourceAllocation requestAllocation = requestPurchaseOrderAllocatedMap.getOrDefault(sourcePurchaseOrderItemId, SourceAllocation.ZERO);
        int availableQuantity = sourceQuantity - persistedAllocation.quantity() - requestAllocation.quantity();
        if (source.quantity() != availableQuantity) {
            return defaultPieceWeightTon;
        }
        BigDecimal currentRemainingWeightTon = TradeItemCalculator.scaleWeightTon(
                remainingWeightTon.subtract(requestAllocation.weightTon())
        );
        BigDecimal exactResidualPieceWeightTon =
                TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(availableQuantity, currentRemainingWeightTon);
        return exactResidualPieceWeightTon != null ? exactResidualPieceWeightTon : defaultPieceWeightTon;
    }

    private BigDecimal resolveSalesOrderWeightTon(
            SalesOrderItemRequest source,
            Map<Long, SourceInboundItemRecord> sourceInboundItemMap,
            Map<Long, SourceAllocation> inboundAllocatedMap,
            Map<Long, SourceAllocation> requestInboundAllocatedMap,
            BigDecimal pieceWeightTon
    ) {
        BigDecimal defaultWeightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), pieceWeightTon);
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId != null) {
            return defaultWeightTon;
        }
        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null || source.quantity() == null || source.quantity() <= 0) {
            return defaultWeightTon;
        }
        SourceInboundItemRecord sourceInboundItem = sourceInboundItemMap.get(sourceInboundItemId);
        if (sourceInboundItem == null || sourceInboundItem.weighWeightTon() == null) {
            return defaultWeightTon;
        }
        int sourceQuantity = sourceInboundItem.quantity() == null ? 0 : sourceInboundItem.quantity();
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
                sourceInboundItem.weighWeightTon()
                        .subtract(persistedAllocation.weightTon())
                        .subtract(requestAllocation.weightTon())
        );
        return residualWeightTon.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE)
                : residualWeightTon;
    }

    private void validateSourceAllocation(
            SalesOrderItemRequest source,
            int lineNo,
            Map<Long, SourceInboundItemRecord> sourceInboundItemMap,
            Map<Long, SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap,
            Map<Long, SourceAllocation> inboundAllocatedMap,
            Map<Long, SourceAllocation> purchaseOrderAllocatedMap,
            Map<Long, SourceAllocation> requestInboundAllocatedMap,
            Map<Long, SourceAllocation> requestPurchaseOrderAllocatedMap
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId != null) {
            SourcePurchaseOrderItemRecord sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(sourcePurchaseOrderItemId);
            if (sourcePurchaseOrderItem == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
            }
            int allocatedQuantity = purchaseOrderAllocatedMap.getOrDefault(sourcePurchaseOrderItemId, SourceAllocation.ZERO).quantity();
            int requestedQuantity = requestPurchaseOrderAllocatedMap.getOrDefault(sourcePurchaseOrderItemId, SourceAllocation.ZERO).quantity();
            validateAvailableQuantity(source.quantity(), sourcePurchaseOrderItem.quantity(), allocatedQuantity, requestedQuantity, lineNo);
            return;
        }

        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null) {
            return;
        }
        SourceInboundItemRecord sourceInboundItem = sourceInboundItemMap.get(sourceInboundItemId);
        if (sourceInboundItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不存在");
        }
        int allocatedQuantity = inboundAllocatedMap.getOrDefault(sourceInboundItemId, SourceAllocation.ZERO).quantity();
        int requestedQuantity = requestInboundAllocatedMap.getOrDefault(sourceInboundItemId, SourceAllocation.ZERO).quantity();
        validateAvailableQuantity(source.quantity(), sourceInboundItem.quantity(), allocatedQuantity, requestedQuantity, lineNo);
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

    private boolean matchesStatusOnlyUpdate(SalesOrder entity, SalesOrderRequest request) {
        if (entity == null || request == null) {
            return false;
        }
        if (!normalize(entity.getOrderNo()).equals(normalize(request.orderNo()))
                || !normalize(entity.getPurchaseInboundNo()).equals(normalize(request.purchaseInboundNo()))
                || !normalize(entity.getPurchaseOrderNo()).equals(normalize(request.purchaseOrderNo()))
                || !normalize(entity.getCustomerName()).equals(normalize(request.customerName()))
                || !normalize(entity.getProjectName()).equals(normalize(request.projectName()))
                || !java.util.Objects.equals(entity.getDeliveryDate(), request.deliveryDate())
                || !normalize(entity.getSalesName()).equals(normalize(request.salesName()))
                || !normalize(entity.getRemark()).equals(normalize(request.remark()))) {
            return false;
        }

        List<SalesOrderItem> entityItems = entity.getItems().stream()
                .sorted(java.util.Comparator.comparing(SalesOrderItem::getLineNo))
                .toList();
        List<SalesOrderItemRequest> requestItems = request.items() == null ? List.of() : request.items();
        if (entityItems.size() != requestItems.size()) {
            return false;
        }
        for (int i = 0; i < entityItems.size(); i++) {
            if (!matchesStatusOnlyUpdateItem(entityItems.get(i), requestItems.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesStatusOnlyUpdateItem(SalesOrderItem entityItem, SalesOrderItemRequest requestItem) {
        return java.util.Objects.equals(entityItem.getId(), requestItem.id())
                && normalize(entityItem.getMaterialCode()).equals(normalize(requestItem.materialCode()))
                && normalize(entityItem.getBrand()).equals(normalize(requestItem.brand()))
                && normalize(entityItem.getCategory()).equals(normalize(requestItem.category()))
                && normalize(entityItem.getMaterial()).equals(normalize(requestItem.material()))
                && normalize(entityItem.getSpec()).equals(normalize(requestItem.spec()))
                && normalize(entityItem.getLength()).equals(normalize(requestItem.length()))
                && normalize(entityItem.getUnit()).equals(normalize(requestItem.unit()))
                && java.util.Objects.equals(entityItem.getSourceInboundItemId(), requestItem.sourceInboundItemId())
                && java.util.Objects.equals(entityItem.getSourcePurchaseOrderItemId(), requestItem.sourcePurchaseOrderItemId())
                && normalize(entityItem.getWarehouseName()).equals(normalize(requestItem.warehouseName()))
                && normalize(entityItem.getBatchNo()).equals(normalize(requestItem.batchNo()))
                && java.util.Objects.equals(entityItem.getQuantity(), requestItem.quantity())
                && TradeItemCalculator.normalizeQuantityUnit(entityItem.getQuantityUnit())
                .equals(TradeItemCalculator.normalizeQuantityUnit(requestItem.quantityUnit()))
                && compareWeight(entityItem.getPieceWeightTon(), requestItem.pieceWeightTon())
                && java.util.Objects.equals(entityItem.getPiecesPerBundle(), requestItem.piecesPerBundle())
                && compareWeight(entityItem.getWeightTon(), requestItem.weightTon())
                && compareAmount(entityItem.getUnitPrice(), requestItem.unitPrice())
                && compareAmount(entityItem.getAmount(), requestItem.amount());
    }

    private boolean compareWeight(BigDecimal left, BigDecimal right) {
        return TradeItemCalculator.scaleWeightTon(left).compareTo(TradeItemCalculator.scaleWeightTon(right)) == 0;
    }

    private boolean compareAmount(BigDecimal left, BigDecimal right) {
        return TradeItemCalculator.scaleAmount(left).compareTo(TradeItemCalculator.scaleAmount(right)) == 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasPurchaseOrderBackedItems(SalesOrder entity) {
        return entity.getItems().stream()
                .anyMatch(item -> item.getSourcePurchaseOrderItemId() != null
                        && item.getQuantity() != null
                        && item.getQuantity() > 0);
    }

    private void finalizePurchaseOrderAllocations(SalesOrder entity) {
        List<Long> sourcePurchaseOrderItemIds = entity.getItems().stream()
                .map(SalesOrderItem::getSourcePurchaseOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SalesOrderItem item : entity.getItems()) {
            BigDecimal weightTon = TradeItemCalculator.scaleWeightTon(item.getWeightTon());
            if (item.getSourcePurchaseOrderItemId() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                int lineNo = item.getLineNo() == null ? 0 : item.getLineNo();
                SourcePurchaseOrderItemRecord sourcePurchaseOrderItem = sourcePurchaseOrderItemMap.get(item.getSourcePurchaseOrderItemId());
                if (sourcePurchaseOrderItem == null) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
                }
                weightTon = purchaseItemPieceWeightAppService.allocateForSalesOrderItem(
                        sourcePurchaseOrderItem.id(),
                        item.getQuantity(),
                        item.getId(),
                        lineNo
                );
                BigDecimal exactPieceWeightTon = TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(
                        item.getQuantity(),
                        weightTon
                );
                if (exactPieceWeightTon != null) {
                    item.setPieceWeightTon(exactPieceWeightTon);
                }
                item.setWeightTon(weightTon);
            }
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, item.getUnitPrice());
            item.setAmount(amount);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }
        entity.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        entity.setTotalAmount(TradeItemCalculator.scaleAmount(totalAmount));
    }

    private record SourceAllocation(
            int quantity,
            BigDecimal weightTon
    ) {

        private static final SourceAllocation ZERO = new SourceAllocation(0, BigDecimal.ZERO.setScale(PrecisionConstants.WEIGHT_SCALE));
    }
}
