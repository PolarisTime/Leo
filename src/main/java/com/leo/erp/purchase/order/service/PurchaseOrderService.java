package com.leo.erp.purchase.order.service;

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
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
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
public class PurchaseOrderService extends AbstractCrudService<PurchaseOrder, PurchaseOrderRequest, PurchaseOrderResponse> {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final SupplierRepository supplierRepository;
    private final PurchaseInboundItemQueryService purchaseInboundItemQueryService;
    private final SalesOrderItemQueryService salesOrderItemQueryService;
    private final PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService;
    private final WorkflowTransitionGuard workflowTransitionGuard;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator,
                                PurchaseOrderMapper purchaseOrderMapper,
                                TradeItemMaterialSupport tradeItemMaterialSupport,
                                WarehouseSelectionSupport warehouseSelectionSupport,
                                SupplierRepository supplierRepository,
                                PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                SalesOrderItemQueryService salesOrderItemQueryService,
                                PurchaseOrderItemPieceWeightService purchaseOrderItemPieceWeightService,
                                WorkflowTransitionGuard workflowTransitionGuard) {
        super(snowflakeIdGenerator);
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.supplierRepository = supplierRepository;
        this.purchaseInboundItemQueryService = purchaseInboundItemQueryService;
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.purchaseOrderItemPieceWeightService = purchaseOrderItemPieceWeightService;
        this.workflowTransitionGuard = workflowTransitionGuard;
    }

    private static final String[] PURCHASE_ORDER_SEARCH_FIELDS = {"orderNo", "supplierName"};

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> page(PageQuery query,
                                            String keyword,
                                            String supplierName,
                                            String status,
                                            java.time.LocalDate startDate,
                                            java.time.LocalDate endDate) {
        Specification<PurchaseOrder> spec = Specs.<PurchaseOrder>notDeleted()
                .and(Specs.keywordLike(keyword, PURCHASE_ORDER_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("supplierName", supplierName))
                .and(Specs.equalIfPresent("status", status))
                .and(Specs.betweenIfPresent("orderDate", startDate, endDate));
        return page(query, spec, purchaseOrderRepository);
    }

    @Transactional(readOnly = true)
    public java.util.List<PurchaseOrderResponse> search(String keyword, int maxSize) {
        return search(keyword, PURCHASE_ORDER_SEARCH_FIELDS, maxSize,
                Specs.notDeleted(), purchaseOrderRepository);
    }

    @Override
    protected PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        Map<Long, Integer> allocatedQuantityMap = loadAllocatedQuantityMap(order);
        Map<Long, Integer> salesAllocatedQuantityMap = loadSalesAllocatedQuantityMap(order);
        Map<Long, BigDecimal> salesRemainingWeightMap = loadSalesRemainingWeightMap(order);
        PurchaseOrderResponse response = purchaseOrderMapper.toResponse(order);
        return new PurchaseOrderResponse(
                response.id(), response.orderNo(), response.supplierName(),
                response.orderDate(), response.buyerName(), response.totalWeight(),
                response.totalAmount(), response.status(), response.remark(),
                order.getItems().stream().map(item -> new PurchaseOrderItemResponse(
                        item.getId(), item.getLineNo(), item.getMaterialCode(),
                        item.getBrand(), item.getCategory(), item.getMaterial(),
                        item.getSpec(), item.getLength(), item.getUnit(), item.getWarehouseName(), item.getBatchNo(),
                        remainingQuantity(item, allocatedQuantityMap),
                        salesRemainingQuantity(item, salesAllocatedQuantityMap),
                        salesRemainingWeightTon(item, salesAllocatedQuantityMap, salesRemainingWeightMap),
                        item.getQuantity(), item.getQuantityUnit(), item.getPieceWeightTon(),
                        item.getPiecesPerBundle(), item.getWeightTon(),
                        item.getUnitPrice(), item.getAmount()
                )).toList()
        );
    }

    private Map<Long, Integer> loadAllocatedQuantityMap(PurchaseOrder order) {
        List<Long> orderItemIds = order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .distinct()
                .toList();
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> summaryMap = purchaseInboundItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(orderItemIds);
        Map<Long, Integer> allocatedMap = new HashMap<>();
        summaryMap.forEach((key, value) -> allocatedMap.put(key, Math.toIntExact(value)));
        return allocatedMap;
    }

    private Map<Long, Integer> loadSalesAllocatedQuantityMap(PurchaseOrder order) {
        List<Long> orderItemIds = order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .distinct()
                .toList();
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> summaryMap = salesOrderItemQueryService.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(orderItemIds, null);
        Map<Long, Integer> allocatedMap = new HashMap<>();
        summaryMap.forEach((key, value) -> allocatedMap.put(key, Math.toIntExact(value)));
        return allocatedMap;
    }

    private Integer remainingQuantity(PurchaseOrderItem item, Map<Long, Integer> allocatedQuantityMap) {
        int allocatedQuantity = allocatedQuantityMap.getOrDefault(item.getId(), 0);
        return Math.max(0, item.getQuantity() - allocatedQuantity);
    }

    private Integer salesRemainingQuantity(PurchaseOrderItem item, Map<Long, Integer> allocatedQuantityMap) {
        int allocatedQuantity = allocatedQuantityMap.getOrDefault(item.getId(), 0);
        return Math.max(0, item.getQuantity() - allocatedQuantity);
    }

    private Map<Long, BigDecimal> loadSalesRemainingWeightMap(PurchaseOrder order) {
        List<Long> orderItemIds = order.getItems().stream()
                .map(PurchaseOrderItem::getId)
                .distinct()
                .toList();
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        return purchaseOrderItemPieceWeightService.summarizeRemainingWeightByPurchaseOrderItemIds(orderItemIds);
    }

    private BigDecimal salesRemainingWeightTon(PurchaseOrderItem item,
                                               Map<Long, Integer> allocatedQuantityMap,
                                               Map<Long, BigDecimal> remainingWeightMap) {
        BigDecimal remainingWeightTon = remainingWeightMap.get(item.getId());
        if (remainingWeightTon != null) {
            return remainingWeightTon;
        }
        int remainingQuantity = salesRemainingQuantity(item, allocatedQuantityMap);
        if (remainingQuantity == item.getQuantity()) {
            return TradeItemCalculator.scaleWeightTon(item.getWeightTon());
        }
        return TradeItemCalculator.calculateWeightTon(remainingQuantity, item.getPieceWeightTon());
    }

    @Override
    protected void validateCreate(PurchaseOrderRequest request) {
        if (purchaseOrderRepository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单号已存在");
        }
    }

    @Override
    protected void validateUpdate(PurchaseOrder purchaseOrder, PurchaseOrderRequest request) {
        if (!purchaseOrder.getOrderNo().equals(request.orderNo())
                && purchaseOrderRepository.existsByOrderNoAndDeletedFlagFalse(request.orderNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单号已存在");
        }
    }

    @Override
    protected PurchaseOrder newEntity() {
        return new PurchaseOrder();
    }

    @Override
    protected void assignId(PurchaseOrder entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<PurchaseOrder> findActiveEntity(Long id) {
        return purchaseOrderRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "采购订单不存在";
    }

    private String requireMasterSupplierName(String supplierName) {
        String normalizedName = supplierName == null ? "" : supplierName.trim();
        return supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(normalizedName)
                .map(Supplier::getSupplierName)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "供应商不存在，请先在主数据供应商资料中维护"
                ));
    }

    @Override
    protected void apply(PurchaseOrder purchaseOrder, PurchaseOrderRequest request) {
        String nextStatus = (request.status() == null || request.status().isBlank()) ? StatusConstants.DRAFT : request.status();
        workflowTransitionGuard.assertAuditPermissionForProtectedValue(
                "purchase-orders",
                purchaseOrder.getStatus(),
                nextStatus,
                StatusConstants.AUDITED,
                StatusConstants.PURCHASE_COMPLETED
        );
        purchaseOrder.setOrderNo(request.orderNo());
        purchaseOrder.setSupplierName(requireMasterSupplierName(request.supplierName()));
        purchaseOrder.setOrderDate(request.orderDate());
        purchaseOrder.setBuyerName(request.buyerName());
        purchaseOrder.setStatus(nextStatus);
        purchaseOrder.setRemark(request.remark());

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        var materialMap = tradeItemMaterialSupport.loadMaterialMap(
                request.items().stream().map(PurchaseOrderItemRequest::materialCode).toList()
        );
        List<PurchaseOrderItem> items = ManagedEntityItemSupport.syncById(
                purchaseOrder.getItems(),
                request.items(),
                PurchaseOrderItem::getId,
                PurchaseOrderItemRequest::id,
                PurchaseOrderItem::new,
                this::nextId,
                PurchaseOrderItem::setId
        );
        Map<Long, BigDecimal> inboundWeightAdjustmentMap =
                purchaseInboundItemQueryService.summarizeWeightAdjustmentBySourcePurchaseOrderItemIds(
                        items.stream().map(PurchaseOrderItem::getId).toList()
                );

        for (int index = 0; index < request.items().size(); index++) {
            PurchaseOrderItemRequest itemRequest = request.items().get(index);
            Material material = materialMap.get(itemRequest.materialCode());
            PurchaseOrderItem item = items.get(index);
            item.setPurchaseOrder(purchaseOrder);
            item.setLineNo(index + 1);
            item.setMaterialCode(itemRequest.materialCode());
            item.setBrand(itemRequest.brand());
            item.setCategory(itemRequest.category());
            item.setMaterial(itemRequest.material());
            item.setSpec(itemRequest.spec());
            item.setLength(itemRequest.length());
            item.setUnit(itemRequest.unit());
            item.setWarehouseName(warehouseSelectionSupport.normalizeWarehouseName(itemRequest.warehouseName(), index + 1, true));
            item.setBatchNo(tradeItemMaterialSupport.normalizeBatchNo(material, itemRequest.batchNo(), index + 1, false));
            item.setQuantity(itemRequest.quantity());
            item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(itemRequest.quantityUnit()));
            item.setPieceWeightTon(itemRequest.pieceWeightTon());
            item.setPiecesPerBundle(itemRequest.piecesPerBundle());
            BigDecimal baseWeightTon = TradeItemCalculator.calculateWeightTon(itemRequest.quantity(), itemRequest.pieceWeightTon());
            BigDecimal weightAdjustmentTon = inboundWeightAdjustmentMap.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal requestedWeightTon = itemRequest.weightTon() == null
                    ? null
                    : TradeItemCalculator.scaleWeightTon(itemRequest.weightTon());
            BigDecimal weightTon = requestedWeightTon != null
                    && weightAdjustmentTon.compareTo(BigDecimal.ZERO) != 0
                    && requestedWeightTon.compareTo(baseWeightTon) == 0
                    ? requestedWeightTon
                    : TradeItemCalculator.scaleWeightTon(baseWeightTon.add(weightAdjustmentTon));
            item.setWeightTon(weightTon);
            item.setUnitPrice(itemRequest.unitPrice());
            BigDecimal amount = TradeItemCalculator.calculateAmount(weightTon, itemRequest.unitPrice());
            item.setAmount(amount);

            totalWeight = totalWeight.add(weightTon);
            totalAmount = totalAmount.add(amount);
        }

        purchaseOrder.getItems().sort(java.util.Comparator.comparing(PurchaseOrderItem::getLineNo));
        purchaseOrder.setTotalWeight(totalWeight);
        purchaseOrder.setTotalAmount(totalAmount);
    }

    @Override
    protected PurchaseOrder saveEntity(PurchaseOrder entity) {
        return purchaseOrderRepository.save(entity);
    }

    @Override
    protected PurchaseOrderResponse toResponse(PurchaseOrder entity) {
        return purchaseOrderMapper.toResponse(entity);
    }
}
