package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundItemRepository;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.purchase.inbound.mapper.PurchaseInboundMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.purchase.inbound.web.dto.*;
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
public class PurchaseInboundService extends AbstractCrudService<PurchaseInbound, PurchaseInboundRequest, PurchaseInboundResponse> {

    private final PurchaseInboundRepository repository;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final PurchaseInboundItemRepository purchaseInboundItemRepository;
    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final SalesOrderItemQueryService salesOrderItemQueryService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public PurchaseInboundService(PurchaseInboundRepository repository,
                                  SnowflakeIdGenerator idGenerator,
                                  PurchaseInboundMapper purchaseInboundMapper,
                                  TradeItemMaterialSupport tradeItemMaterialSupport,
                                  WarehouseSelectionSupport warehouseSelectionSupport,
                                  PurchaseInboundItemRepository purchaseInboundItemRepository,
                                  PurchaseOrderItemQueryService purchaseOrderItemQueryService,
                                  SalesOrderItemQueryService salesOrderItemQueryService,
                                  WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.purchaseInboundMapper = purchaseInboundMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.purchaseInboundItemRepository = purchaseInboundItemRepository;
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    @Transactional(readOnly = true)
    public Page<PurchaseInboundResponse> page(PageQuery query,
                                              String keyword,
                                              String supplierName,
                                              String status,
                                              java.time.LocalDate startDate,
                                              java.time.LocalDate endDate) {
        Specification<PurchaseInbound> spec = Specs.<PurchaseInbound>notDeleted()
                .and(Specs.keywordLike(keyword, "inboundNo", "purchaseOrderNo", "supplierName"))
                .and(Specs.equalIfPresent("supplierName", supplierName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("inboundDate", startDate, endDate));
        return page(query, spec, repository);
    }

    private static final String[] INBOUND_SEARCH_FIELDS = {"inboundNo", "purchaseOrderNo", "supplierName"};

    @Transactional(readOnly = true)
    public java.util.List<PurchaseInboundResponse> search(String keyword, int maxSize) {
        return search(keyword, INBOUND_SEARCH_FIELDS, maxSize, Specs.notDeleted(), repository);
    }

    @Override
    protected PurchaseInboundResponse toDetailResponse(PurchaseInbound inbound) {
        Map<Long, Integer> allocatedQuantityMap = loadAllocatedQuantityMap(inbound);
        PurchaseInboundResponse response = purchaseInboundMapper.toResponse(inbound);
        return new PurchaseInboundResponse(
                response.id(), response.inboundNo(), response.purchaseOrderNo(),
                response.supplierName(), response.warehouseName(), response.inboundDate(),
                response.settlementMode(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                inbound.getItems().stream().map(item -> new PurchaseInboundItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(), item.getSourcePurchaseOrderItemId(), item.getWarehouseName(), item.getBatchNo(),
                        remainingQuantity(item, allocatedQuantityMap), item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle(),
                        item.getWeightTon(), item.getUnitPrice(), item.getAmount()
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
        Map<Long, Long> summaryMap = salesOrderItemQueryService.summarizeAllocatedQuantityBySourceInboundItemIds(inboundItemIds, null);
        Map<Long, Integer> allocatedMap = new HashMap<>();
        summaryMap.forEach((key, value) -> allocatedMap.put(key, Math.toIntExact(value)));
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

        return purchaseOrderItemQueryService.findActiveByIdIn(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseOrderItem::getId, item -> item));
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
        if (!inbound.getInboundNo().equals(request.inboundNo()) && repository.existsByInboundNoAndDeletedFlagFalse(request.inboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购入库单号已存在");
        }
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
    protected String notFoundMessage() {
        return "采购入库不存在";
    }

    @Override
    protected void apply(PurchaseInbound inbound, PurchaseInboundRequest request) {
        String nextStatus = (request.status() == null || request.status().isBlank()) ? "草稿" : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-inbounds",
                inbound.getStatus(),
                nextStatus,
                "已审核",
                "完成入库"
        );
        inbound.setInboundNo(request.inboundNo());
        inbound.setPurchaseOrderNo(request.purchaseOrderNo());
        inbound.setSupplierName(request.supplierName());
        inbound.setWarehouseName(request.warehouseName());
        inbound.setInboundDate(request.inboundDate());
        inbound.setSettlementMode(request.settlementMode());
        inbound.setStatus(nextStatus);
        inbound.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(PurchaseInboundItemRequest::materialCode).toList()
        );
        List<Long> sourcePurchaseOrderItemIds = extractSourcePurchaseOrderItemIds(request);
        Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap = loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds);
        Map<Long, Integer> allocatedQuantityMap = loadAllocatedQuantityMap(sourcePurchaseOrderItemIds, inbound.getId());
        Map<Long, Integer> requestAllocatedQuantityMap = new HashMap<>();
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
            item.setPurchaseInbound(inbound);
            item.setLineNo(i + 1);
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
            validateSourcePurchaseOrderAllocation(source, i + 1, sourcePurchaseOrderItemMap, allocatedQuantityMap, requestAllocatedQuantityMap);
            item.setWarehouseName(warehouseSelectionSupport.normalizeWarehouseName(
                    source.warehouseName() == null || source.warehouseName().isBlank() ? request.warehouseName() : source.warehouseName(),
                    i + 1,
                    true
            ));
            item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), i + 1, true));
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            item.setPieceWeightTon(source.pieceWeightTon());
            item.setPiecesPerBundle(source.piecesPerBundle());
            BigDecimal weightTon = TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon());
            item.setWeightTon(weightTon);
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
            item.setAmount(amount);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }
        inbound.getItems().sort(java.util.Comparator.comparing(PurchaseInboundItem::getLineNo));
        inbound.setTotalWeight(totalWeight);
        inbound.setTotalAmount(totalAmount);
    }

    @Override
    protected PurchaseInbound saveEntity(PurchaseInbound entity) {
        return repository.save(entity);
    }

    @Override
    protected PurchaseInboundResponse toResponse(PurchaseInbound entity) {
        return purchaseInboundMapper.toResponse(entity);
    }
}
