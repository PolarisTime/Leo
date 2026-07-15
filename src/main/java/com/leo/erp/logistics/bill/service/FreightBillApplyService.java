package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.logistics.bill.repository.FreightBillSourceOrderRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.security.permission.DataScopeContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@Service
public class FreightBillApplyService {

    private static final Set<String> ALLOWED_SOURCE_STATUS = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.DELIVERY_VERIFICATION,
            StatusConstants.SALES_COMPLETED
    );

    private final SalesOrderRepository salesOrderRepository;
    private final FreightBillSourceOrderRepository sourceOrderRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public FreightBillApplyService(SalesOrderRepository salesOrderRepository,
                                   FreightBillSourceOrderRepository sourceOrderRepository,
                                   SourceAllocationLockService sourceAllocationLockService) {
        this.salesOrderRepository = salesOrderRepository;
        this.sourceOrderRepository = sourceOrderRepository;
        this.sourceAllocationLockService = sourceAllocationLockService;
    }

    void applyItems(FreightBill entity, FreightBillRequest request, LongSupplier nextId) {
        SourceSnapshot sourceSnapshot = resolveSources(entity, request.items());
        List<FreightBillItem> items = ManagedEntityItemSupport.syncById(
                entity.getItems(),
                request.items(),
                FreightBillItem::getId,
                FreightBillItemRequest::id,
                FreightBillItem::new,
                nextId,
                FreightBillItem::setId
        );
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (int index = 0; index < request.items().size(); index++) {
            FreightBillItem item = items.get(index);
            FreightBillItemRequest source = request.items().get(index);
            SalesOrderItem sourceItem = sourceSnapshot.itemById().get(source.sourceSalesOrderItemId());
            applyItem(entity, item, sourceItem, index + 1);
            totalWeight = totalWeight.add(item.getWeightTon());
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightBillItem::getLineNo));
        entity.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        entity.setTotalFreight(totalWeight.multiply(request.unitPrice())
                .setScale(PrecisionConstants.AMOUNT_SCALE, PrecisionConstants.DEFAULT_ROUNDING));
        syncSourceOrders(entity, sourceSnapshot.orders(), nextId);
    }

    private void applyItem(FreightBill entity,
                           FreightBillItem item,
                           SalesOrderItem source,
                           int lineNo) {
        SalesOrder sourceOrder = source.getSalesOrder();
        item.setFreightBill(entity);
        item.setLineNo(lineNo);
        item.setSourceNo(sourceOrder.getOrderNo());
        item.setSourceSalesOrderItemId(source.getId());
        item.setSettlementCompanyId(source.getSettlementCompanyId());
        item.setSettlementCompanyName(source.getSettlementCompanyName());
        item.setCustomerId(sourceOrder.getCustomerId());
        item.setCustomerName(sourceOrder.getCustomerName());
        item.setProjectId(sourceOrder.getProjectId());
        item.setProjectName(sourceOrder.getProjectName());
        item.setMaterialId(source.getMaterialId());
        item.setMaterialCode(source.getMaterialCode());
        item.setMaterialName(source.getBrand());
        item.setBrand(source.getBrand());
        item.setCategory(source.getCategory());
        item.setMaterial(source.getMaterial());
        item.setSpec(source.getSpec());
        item.setLength(source.getLength());
        item.setQuantity(source.getQuantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.getQuantityUnit()));
        item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(source.getPieceWeightTon()));
        item.setPiecesPerBundle(source.getPiecesPerBundle());
        item.setBatchNo(source.getBatchNo());
        item.setWeightTon(requirePositiveWeight(source));
        item.setWarehouseId(source.getWarehouseId());
        item.setWarehouseName(source.getWarehouseName());
    }

    private SourceSnapshot resolveSources(FreightBill entity, List<FreightBillItemRequest> requestedItems) {
        LinkedHashSet<Long> requestedItemIds = requestedItems.stream()
                .map(FreightBillItemRequest::sourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedItemIds.size() != requestedItems.size()) {
            throw business("物流单必须整单导入销售订单明细，来源明细ID不能为空或重复");
        }
        List<SalesOrder> orders = salesOrderRepository.findAllWithItemsBySourceItemIds(requestedItemIds);
        LinkedHashSet<Long> sourceOrderIds = orders.stream()
                .map(SalesOrder::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        sourceAllocationLockService.lockDocumentSources(List.of(), sourceOrderIds, List.of(), List.of());
        orders = salesOrderRepository.findAllWithItemsBySourceItemIds(requestedItemIds);
        validateOrders(orders);
        LinkedHashSet<Long> completeItemIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(SalesOrderItem::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!completeItemIds.equals(requestedItemIds)) {
            throw business("物流单必须包含所选销售订单的全部明细");
        }
        assertSourceSetImmutable(entity, sourceOrderIds);
        List<Long> occupied = sourceOrderRepository.findOccupiedSourceOrderIds(sourceOrderIds, entity.getId());
        if (!occupied.isEmpty()) {
            throw business("销售订单已关联其他物流单，不能重复导入");
        }
        Map<Long, SalesOrderItem> itemById = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.toMap(SalesOrderItem::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        return new SourceSnapshot(orders, itemById);
    }

    private void validateOrders(List<SalesOrder> orders) {
        if (orders.isEmpty()) {
            throw business("物流单至少需要导入一张销售订单");
        }
        for (SalesOrder order : orders) {
            DataScopeContext.assertCanAccess(order);
            if (!ALLOWED_SOURCE_STATUS.contains(order.getStatus())) {
                throw business("销售订单" + order.getOrderNo() + "当前状态不能生成物流单");
            }
        }
    }

    private void assertSourceSetImmutable(FreightBill entity, Set<Long> requestedOrderIds) {
        Set<Long> currentOrderIds = entity.getSourceOrders().stream()
                .filter(FreightBillSourceOrder::isActiveFlag)
                .map(FreightBillSourceOrder::getSourceSalesOrderId)
                .collect(Collectors.toSet());
        if (!currentOrderIds.isEmpty() && !currentOrderIds.equals(requestedOrderIds)) {
            throw business("物流单保存后不能新增、移除或更换来源销售订单");
        }
    }

    private void syncSourceOrders(FreightBill entity, List<SalesOrder> orders, LongSupplier nextId) {
        Set<Long> existingIds = entity.getSourceOrders().stream()
                .map(FreightBillSourceOrder::getSourceSalesOrderId)
                .collect(Collectors.toSet());
        for (SalesOrder order : orders) {
            if (existingIds.contains(order.getId())) {
                continue;
            }
            FreightBillSourceOrder relation = new FreightBillSourceOrder();
            relation.setId(nextId.getAsLong());
            relation.setFreightBill(entity);
            relation.setSourceSalesOrderId(order.getId());
            relation.setSourceSalesOrderNo(order.getOrderNo());
            relation.setActiveFlag(true);
            entity.getSourceOrders().add(relation);
        }
    }

    private BigDecimal requirePositiveWeight(SalesOrderItem source) {
        BigDecimal weight = TradeItemCalculator.scaleWeightTon(source.getWeightTon());
        if (weight.signum() <= 0) {
            throw business("销售订单" + source.getSalesOrder().getOrderNo() + "存在重量小于等于0的明细");
        }
        return weight;
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    private record SourceSnapshot(List<SalesOrder> orders, Map<Long, SalesOrderItem> itemById) {
    }
}
