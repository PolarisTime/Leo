package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.BusinessStatusValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.service.SalesOrderCompletionSyncService;
import com.leo.erp.sales.outbound.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class SalesOutboundService extends AbstractCrudService<SalesOutbound, SalesOutboundRequest, SalesOutboundResponse> {

    private final SalesOutboundRepository repository;
    private final SalesOutboundMapper salesOutboundMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final WorkflowTransitionGuard workflowTransitionGuard;
    private final SalesOrderCompletionSyncService salesOrderCompletionSyncService;
    private final SalesOrderItemQueryService salesOrderItemQueryService;

    public SalesOutboundService(SalesOutboundRepository repository,
                                SnowflakeIdGenerator idGenerator,
                                SalesOutboundMapper salesOutboundMapper,
                                TradeItemMaterialSupport tradeItemMaterialSupport,
                                WarehouseSelectionSupport warehouseSelectionSupport,
                                WorkflowTransitionGuard workflowTransitionGuard,
                                SalesOrderCompletionSyncService salesOrderCompletionSyncService,
                                SalesOrderItemQueryService salesOrderItemQueryService) {
        super(idGenerator);
        this.repository = repository;
        this.salesOutboundMapper = salesOutboundMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.workflowTransitionGuard = workflowTransitionGuard;
        this.salesOrderCompletionSyncService = salesOrderCompletionSyncService;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
    }

    @Transactional(readOnly = true)
    public Page<SalesOutboundResponse> page(PageQuery query,
                                            String keyword,
                                            String customerName,
                                            String projectName,
                                            String status,
                                            java.time.LocalDate startDate,
                                            java.time.LocalDate endDate) {
        Specification<SalesOutbound> spec = Specs.<SalesOutbound>keywordLike(keyword, "outboundNo", "salesOrderNo", "customerName", "projectName")
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("projectName", projectName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("outboundDate", startDate, endDate));
        return page(query, spec, repository);
    }

    private static final String[] OUTBOUND_SEARCH_FIELDS = {"outboundNo", "salesOrderNo", "customerName", "projectName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOutboundResponse> search(String keyword, int maxSize) {
        return search(keyword, OUTBOUND_SEARCH_FIELDS, maxSize, null, repository);
    }

    @Override
    protected SalesOutboundResponse toDetailResponse(SalesOutbound entity) {
        SalesOutboundResponse response = salesOutboundMapper.toResponse(entity);
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(entity.getItems());
        return new SalesOutboundResponse(
                response.id(), response.outboundNo(), response.salesOrderNo(),
                response.customerName(), response.projectName(), response.warehouseName(),
                response.outboundDate(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                entity.getItems().stream().map(item -> new SalesOutboundItemResponse(
                        item.getId(), item.getLineNo(), resolveItemSourceNo(item, sourceSalesOrderItemMap), item.getSourceSalesOrderItemId(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(), item.getWarehouseName(), item.getBatchNo(),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(), item.getPiecesPerBundle(),
                        item.getWeightTon(), item.getUnitPrice(), item.getAmount()
                )).toList()
        );
    }

    @Override
    protected void validateCreate(SalesOutboundRequest request) {
        if (repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    @Override
    protected void validateUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        if (!entity.getOutboundNo().equals(request.outboundNo()) && repository.existsByOutboundNoAndDeletedFlagFalse(request.outboundNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库单号已存在");
        }
    }

    @Override
    protected SalesOutboundRequest normalizeCreateRequest(SalesOutboundRequest request, long entityId) {
        return new SalesOutboundRequest(
                resolveCreateBusinessNo("sales-outbound", request.outboundNo(), entityId),
                null,
                request.customerName(),
                request.projectName(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesOutboundRequest normalizeUpdateRequest(SalesOutbound entity, SalesOutboundRequest request) {
        return new SalesOutboundRequest(
                entity.getOutboundNo(),
                entity.getSalesOrderNo(),
                request.customerName(),
                request.projectName(),
                request.warehouseName(),
                request.outboundDate(),
                request.status(),
                request.remark(),
                request.items()
        );
    }

    @Override
    protected SalesOutbound newEntity() {
        return new SalesOutbound();
    }

    @Override
    protected void assignId(SalesOutbound entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SalesOutbound> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected Optional<SalesOutbound> findVisibleEntity(Long id) {
        return repository.findById(id);
    }

    @Override
    protected String notFoundMessage() {
        return "销售出库不存在";
    }

    @Override
    protected boolean allowAdminViewDeletedRecords() {
        return true;
    }

    @Override
    protected void apply(SalesOutbound entity, SalesOutboundRequest request) {
        String nextStatus = BusinessStatusValidator.normalizeWithDefault(
                request.status(),
                StatusConstants.DRAFT,
                "销售出库状态",
                StatusConstants.ALLOWED_SALES_OUTBOUND_STATUS
        );
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-outbound",
                entity.getStatus(),
                nextStatus,
                StatusConstants.AUDITED
        );
        entity.setOutboundNo(request.outboundNo());
        entity.setSalesOrderNo(request.salesOrderNo());
        entity.setCustomerName(request.customerName());
        entity.setProjectName(request.projectName());
        entity.setOutboundDate(request.outboundDate());
        entity.setStatus(nextStatus);
        entity.setRemark(request.remark());

        assertSourceSalesOrderItemsNotOccupied(request.items(), entity.getId());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        String firstLineWarehouseName = null;
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(SalesOutboundItemRequest::materialCode).toList()
        );
        List<SalesOutboundItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                SalesOutboundItem::getId,
                SalesOutboundItemRequest::id,
                SalesOutboundItem::new,
                this::nextId,
                SalesOutboundItem::setId
        );
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap = loadSourceSalesOrderItemMap(request.items(), items);
        LinkedHashSet<String> sourceSalesOrderNos = new LinkedHashSet<>();
        for (int i = 0; i < request.items().size(); i++) {
            SalesOutboundItemRequest source = request.items().get(i);
            Material material = materialMap.get(source.materialCode());
            SalesOutboundItem item = items.get(i);
            item.setSalesOutbound(entity);
            item.setLineNo(i + 1);
            Long sourceSalesOrderItemId = resolveSourceSalesOrderItemId(source, item);
            item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
            item.setMaterialCode(source.materialCode());
            item.setBrand(source.brand());
            item.setCategory(source.category());
            item.setMaterial(source.material());
            item.setSpec(source.spec());
            item.setLength(source.length());
            item.setUnit(source.unit());
            String warehouseName = warehouseSelectionSupport.normalizeWarehouseName(
                    source.warehouseName() == null || source.warehouseName().isBlank() ? request.warehouseName() : source.warehouseName(),
                    i + 1,
                    true
            );
            if (firstLineWarehouseName == null) {
                firstLineWarehouseName = warehouseName;
            }
            item.setWarehouseName(warehouseName);
            item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, source.batchNo(), i + 1, true));
            item.setQuantity(source.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
            BigDecimal weightTon = source.weightTon() == null
                    ? TradeItemCalculator.calculateWeightTon(source.quantity(), source.pieceWeightTon())
                    : TradeItemCalculator.scaleWeightTon(source.weightTon());
            BigDecimal pieceWeightTon = TradeItemCalculator.calculateRepresentableAveragePieceWeightTon(source.quantity(), weightTon);
            item.setPieceWeightTon(pieceWeightTon != null ? pieceWeightTon : TradeItemCalculator.scaleWeightTon(source.pieceWeightTon()));
            item.setPiecesPerBundle(source.piecesPerBundle());
            item.setWeightTon(weightTon);
            item.setUnitPrice(source.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, source.unitPrice());
            item.setAmount(amount);
            collectSourceSalesOrderNos(sourceSalesOrderNos, source, sourceSalesOrderItemMap, sourceSalesOrderItemId);
            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }
        entity.getItems().sort(java.util.Comparator.comparing(SalesOutboundItem::getLineNo));
        entity.setSalesOrderNo(sourceSalesOrderNos.isEmpty()
                ? trimToNull(request.salesOrderNo())
                : String.join(", ", sourceSalesOrderNos));
        entity.setWarehouseName(firstLineWarehouseName == null ? trimToNull(request.warehouseName()) : firstLineWarehouseName);
        entity.setTotalWeight(totalWeight);
        entity.setTotalAmount(totalAmount);
    }

    private void assertSourceSalesOrderItemsNotOccupied(
            Collection<SalesOutboundItemRequest> items,
            Long currentOutboundId
    ) {
        LinkedHashSet<Long> sourceSalesOrderItemIds = items.stream()
                .map(SalesOutboundItemRequest::sourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (sourceSalesOrderItemIds.isEmpty()) {
            return;
        }

        List<SalesOutbound> occupiedOutbounds =
                repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(
                        sourceSalesOrderItemIds,
                        currentOutboundId
                );
        for (Long sourceSalesOrderItemId : sourceSalesOrderItemIds) {
            for (SalesOutbound occupiedOutbound : occupiedOutbounds) {
                boolean matched = occupiedOutbound.getItems().stream()
                        .anyMatch(item -> sourceSalesOrderItemId.equals(item.getSourceSalesOrderItemId()));
                if (!matched) {
                    continue;
                }
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "销售订单明细已被销售出库单" + occupiedOutbound.getOutboundNo() + "关联"
                );
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Override
    protected SalesOutbound saveEntity(SalesOutbound entity) {
        SalesOutbound saved = repository.save(entity);
        salesOrderCompletionSyncService.syncBySalesOrderReference(saved.getSalesOrderNo());
        return saved;
    }

    @Override
    protected SalesOutboundResponse toResponse(SalesOutbound entity) {
        return salesOutboundMapper.toResponse(entity);
    }

    private Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<SalesOutboundItemRequest> requestItems,
                                                                  List<SalesOutboundItem> items) {
        LinkedHashSet<Long> sourceSalesOrderItemIds = new LinkedHashSet<>();
        requestItems.stream()
                .map(SalesOutboundItemRequest::sourceSalesOrderItemId)
                .filter(id -> id != null)
                .forEach(sourceSalesOrderItemIds::add);
        items.stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(id -> id != null)
                .forEach(sourceSalesOrderItemIds::add);
        if (sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return salesOrderItemQueryService.findActiveByIdIn(sourceSalesOrderItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderItem::getId, item -> item));
    }

    private Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<SalesOutboundItem> items) {
        List<Long> sourceSalesOrderItemIds = items.stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return salesOrderItemQueryService.findActiveByIdIn(sourceSalesOrderItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderItem::getId, item -> item));
    }

    private Long resolveSourceSalesOrderItemId(SalesOutboundItemRequest source, SalesOutboundItem item) {
        if (source.sourceSalesOrderItemId() != null) {
            return source.sourceSalesOrderItemId();
        }
        return item.getSourceSalesOrderItemId();
    }

    private void collectSourceSalesOrderNos(LinkedHashSet<String> sourceSalesOrderNos,
                                            SalesOutboundItemRequest source,
                                            Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                            Long sourceSalesOrderItemId) {
        if (sourceSalesOrderItemId != null) {
            SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
            if (sourceSalesOrderItem == null || sourceSalesOrderItem.getSalesOrder() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "来源销售订单明细不存在");
            }
            sourceSalesOrderNos.add(sourceSalesOrderItem.getSalesOrder().getOrderNo());
            return;
        }
        String sourceNo = trimToNull(source.sourceNo());
        if (sourceNo != null) {
            sourceSalesOrderNos.add(sourceNo);
        }
    }

    private String resolveItemSourceNo(SalesOutboundItem item, Map<Long, SalesOrderItem> sourceSalesOrderItemMap) {
        if (item.getSourceSalesOrderItemId() == null) {
            return null;
        }
        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(item.getSourceSalesOrderItemId());
        if (sourceSalesOrderItem == null || sourceSalesOrderItem.getSalesOrder() == null) {
            return null;
        }
        return sourceSalesOrderItem.getSalesOrder().getOrderNo();
    }
}
