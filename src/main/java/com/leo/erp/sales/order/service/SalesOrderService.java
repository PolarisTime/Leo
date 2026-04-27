package com.leo.erp.sales.order.service;

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
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
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
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public SalesOrderService(SalesOrderRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             SalesOrderMapper salesOrderMapper,
                             TradeItemMaterialSupport tradeItemMaterialSupport,
                             PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                             SalesOrderItemRepository salesOrderItemRepository,
                             WarehouseSelectionSupport warehouseSelectionSupport,
                             WorkflowTransitionGuard workflowTransitionGuard) {
        super(idGenerator);
        this.repository = repository;
        this.salesOrderMapper = salesOrderMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
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
                .and(Specs.keywordLike(keyword, "orderNo", "customerName", "projectName"))
                .and(Specs.equalIfPresent("customerName", customerName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("deliveryDate", startDate, endDate));
        return page(query, spec, repository);
    }

    private static final String[] SALES_ORDER_SEARCH_FIELDS = {"orderNo", "customerName", "projectName"};

    @Transactional(readOnly = true)
    public java.util.List<SalesOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, SALES_ORDER_SEARCH_FIELDS, maxSize, Specs.notDeleted(), repository);
    }

    @Override
    protected SalesOrderResponse toDetailResponse(SalesOrder entity) {
        SalesOrderResponse response = salesOrderMapper.toResponse(entity);
        return new SalesOrderResponse(
                response.id(), response.orderNo(), response.purchaseInboundNo(),
                response.customerName(), response.projectName(), response.deliveryDate(),
                response.salesName(), response.totalWeight(), response.totalAmount(),
                response.status(), response.remark(),
                entity.getItems().stream().map(item -> new SalesOrderItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(), item.getSourceInboundItemId(), item.getWarehouseName(), item.getBatchNo(),
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
        String nextStatus = (request.status() == null || request.status().isBlank()) ? "草稿" : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "sales-orders",
                entity.getStatus(),
                nextStatus,
                "已审核",
                "完成销售"
        );
        entity.setOrderNo(request.orderNo());
        entity.setPurchaseInboundNo(request.purchaseInboundNo());
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
        Map<Long, PurchaseInboundItem> sourceInboundItemMap = loadSourceInboundItemMap(sourceInboundItemIds);
        Map<Long, Integer> allocatedQuantityMap = loadAllocatedQuantityMap(sourceInboundItemIds, entity.getId());
        Map<Long, Integer> requestAllocatedQuantityMap = new HashMap<>();
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
            item.setWarehouseName(warehouseSelectionSupport.normalizeWarehouseName(source.warehouseName(), i + 1, true));
            validateSourceInboundAllocation(source, i + 1, sourceInboundItemMap, allocatedQuantityMap, requestAllocatedQuantityMap);
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

    private Map<Long, PurchaseInboundItem> loadSourceInboundItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        return purchaseInboundItemQueryService.findAllActiveByIdIn(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseInboundItem::getId, item -> item));
    }

    private Map<Long, Integer> loadAllocatedQuantityMap(List<Long> sourceInboundItemIds, Long currentOrderId) {
        if (sourceInboundItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> allocatedQuantityMap = new HashMap<>();
        salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(sourceInboundItemIds, currentOrderId)
                .forEach(summary -> allocatedQuantityMap.put(
                        summary.getSourceInboundItemId(),
                        Math.toIntExact(summary.getTotalQuantity())
                ));
        return allocatedQuantityMap;
    }

    private void validateSourceInboundAllocation(
            SalesOrderItemRequest source,
            int lineNo,
            Map<Long, PurchaseInboundItem> sourceInboundItemMap,
            Map<Long, Integer> allocatedQuantityMap,
            Map<Long, Integer> requestAllocatedQuantityMap
    ) {
        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null) {
            return;
        }
        PurchaseInboundItem sourceInboundItem = sourceInboundItemMap.get(sourceInboundItemId);
        if (sourceInboundItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不存在");
        }
        int allocatedQuantity = allocatedQuantityMap.getOrDefault(sourceInboundItemId, 0);
        int requestedQuantity = requestAllocatedQuantityMap.getOrDefault(sourceInboundItemId, 0);
        int availableQuantity = sourceInboundItem.getQuantity() - allocatedQuantity;
        if (source.quantity() + requestedQuantity > availableQuantity) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行可关联数量不足，剩余可用 " + Math.max(availableQuantity - requestedQuantity, 0) + " 件"
            );
        }
        requestAllocatedQuantityMap.merge(sourceInboundItemId, source.quantity(), Integer::sum);
    }
}
