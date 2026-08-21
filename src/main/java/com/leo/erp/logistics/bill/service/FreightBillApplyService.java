package com.leo.erp.logistics.bill.service;

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
import com.leo.erp.sales.api.SalesOrderLogisticsSourceQuery;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
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

    private final SalesOrderLogisticsSourceQuery salesOrderSourceQuery;
    private final FreightBillSourceOrderRepository sourceOrderRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public FreightBillApplyService(SalesOrderLogisticsSourceQuery salesOrderSourceQuery,
                                   FreightBillSourceOrderRepository sourceOrderRepository,
                                   SourceAllocationLockService sourceAllocationLockService) {
        this.salesOrderSourceQuery = salesOrderSourceQuery;
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
            SalesOrderSourceItemSnapshot sourceItem = sourceSnapshot.itemById()
                    .get(source.sourceSalesOrderItemId());
            SalesOrderSourceSnapshot sourceOrder = sourceSnapshot.orderByItemId()
                    .get(source.sourceSalesOrderItemId());
            applyItem(entity, item, sourceItem, sourceOrder, index + 1);
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
                           SalesOrderSourceItemSnapshot source,
                           SalesOrderSourceSnapshot sourceOrder,
                           int lineNo) {
        item.setFreightBill(entity);
        item.setLineNo(lineNo);
        item.setSourceNo(sourceOrder.orderNo());
        item.setSourceSalesOrderItemId(source.id());
        item.setSettlementCompanyId(source.settlementCompanyId());
        item.setSettlementCompanyName(source.settlementCompanyName());
        item.setCustomerId(sourceOrder.customerId());
        item.setCustomerName(sourceOrder.customerName());
        item.setProjectId(sourceOrder.projectId());
        item.setProjectName(sourceOrder.projectName());
        item.setMaterialId(source.materialId());
        item.setMaterialCode(source.materialCode());
        // material_name 为历史遗留列：上游快照无独立物料名称数据源，
        // 全链路（前端导入映射、运费对账单读取）均以品牌值填充，保持一致。
        item.setMaterialName(source.brand());
        item.setBrand(source.brand());
        item.setCategory(source.category());
        item.setMaterial(source.material());
        item.setSpec(source.spec());
        item.setLength(source.length());
        item.setQuantity(source.quantity());
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(source.pieceWeightTon()));
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setBatchNo(source.batchNo());
        item.setWeightTon(requirePositiveWeight(source, sourceOrder.orderNo()));
        item.setWarehouseId(source.warehouseId());
        item.setWarehouseName(source.warehouseName());
    }

    private SourceSnapshot resolveSources(FreightBill entity, List<FreightBillItemRequest> requestedItems) {
        LinkedHashSet<Long> requestedItemIds = requestedItems.stream()
                .map(FreightBillItemRequest::sourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedItemIds.size() != requestedItems.size()) {
            throw business("物流单必须整单导入销售订单明细，来源明细ID不能为空或重复");
        }
        List<SalesOrderSourceSnapshot> orders = salesOrderSourceQuery.findBySourceItemIds(requestedItemIds);
        LinkedHashSet<Long> sourceOrderIds = orders.stream()
                .map(SalesOrderSourceSnapshot::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        sourceAllocationLockService.lockDocumentSources(List.of(), sourceOrderIds, List.of(), List.of());
        orders = salesOrderSourceQuery.findBySourceItemIds(requestedItemIds);
        validateOrders(orders);
        LinkedHashSet<Long> completeItemIds = orders.stream()
                .flatMap(order -> order.items().stream())
                .map(SalesOrderSourceItemSnapshot::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!completeItemIds.equals(requestedItemIds)) {
            throw business("物流单必须包含所选销售订单的全部明细");
        }
        assertSourceSetImmutable(entity, sourceOrderIds);
        List<Long> occupied = sourceOrderRepository.findOccupiedSourceOrderIds(sourceOrderIds, entity.getId());
        if (!occupied.isEmpty()) {
            throw business("销售订单已关联其他物流单，不能重复导入");
        }
        Map<Long, SalesOrderSourceItemSnapshot> itemById = new LinkedHashMap<>();
        Map<Long, SalesOrderSourceSnapshot> orderByItemId = new LinkedHashMap<>();
        for (SalesOrderSourceSnapshot order : orders) {
            for (SalesOrderSourceItemSnapshot item : order.items()) {
                itemById.putIfAbsent(item.id(), item);
                orderByItemId.putIfAbsent(item.id(), order);
            }
        }
        return new SourceSnapshot(orders, itemById, orderByItemId);
    }

    private void validateOrders(List<SalesOrderSourceSnapshot> orders) {
        if (orders.isEmpty()) {
            throw business("物流单至少需要导入一张销售订单");
        }
        for (SalesOrderSourceSnapshot order : orders) {
            if (!ALLOWED_SOURCE_STATUS.contains(order.status())) {
                throw business("销售订单" + order.orderNo() + "当前状态不能生成物流单");
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

    private void syncSourceOrders(FreightBill entity,
                                  List<SalesOrderSourceSnapshot> orders,
                                  LongSupplier nextId) {
        Set<Long> existingIds = entity.getSourceOrders().stream()
                .map(FreightBillSourceOrder::getSourceSalesOrderId)
                .collect(Collectors.toSet());
        for (SalesOrderSourceSnapshot order : orders) {
            if (existingIds.contains(order.id())) {
                continue;
            }
            FreightBillSourceOrder relation = new FreightBillSourceOrder();
            relation.setId(nextId.getAsLong());
            relation.setFreightBill(entity);
            relation.setSourceSalesOrderId(order.id());
            relation.setSourceSalesOrderNo(order.orderNo());
            relation.setActiveFlag(true);
            entity.getSourceOrders().add(relation);
        }
    }

    private BigDecimal requirePositiveWeight(SalesOrderSourceItemSnapshot source, String orderNo) {
        BigDecimal weight = TradeItemCalculator.scaleWeightTon(source.weightTon());
        if (weight.signum() <= 0) {
            throw business("销售订单" + orderNo + "存在重量小于等于0的明细");
        }
        return weight;
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    private record SourceSnapshot(
            List<SalesOrderSourceSnapshot> orders,
            Map<Long, SalesOrderSourceItemSnapshot> itemById,
            Map<Long, SalesOrderSourceSnapshot> orderByItemId
    ) {
    }
}
