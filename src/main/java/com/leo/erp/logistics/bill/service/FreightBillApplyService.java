package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.ManagedEntityItemSupport;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceItem;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.logistics.bill.repository.FreightBillSourceItemRepository;
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
    private final FreightBillSourceItemRepository sourceItemRepository;
    private final SourceAllocationLockService sourceAllocationLockService;

    public FreightBillApplyService(SalesOrderLogisticsSourceQuery salesOrderSourceQuery,
                                   FreightBillSourceItemRepository sourceItemRepository,
                                   SourceAllocationLockService sourceAllocationLockService) {
        this.salesOrderSourceQuery = salesOrderSourceQuery;
        this.sourceItemRepository = sourceItemRepository;
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
        Map<Long, Integer> appliedQuantityByItemId = new LinkedHashMap<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (int index = 0; index < request.items().size(); index++) {
            FreightBillItem item = items.get(index);
            FreightBillItemRequest source = request.items().get(index);
            SalesOrderSourceItemSnapshot sourceItem = sourceSnapshot.itemById()
                    .get(source.sourceSalesOrderItemId());
            SalesOrderSourceSnapshot sourceOrder = sourceSnapshot.orderByItemId()
                    .get(source.sourceSalesOrderItemId());
            int quantity = resolveQuantity(source, sourceItem, sourceSnapshot, index + 1);
            applyItem(entity, item, source, sourceItem, sourceOrder, quantity, index + 1);
            appliedQuantityByItemId.merge(sourceItem.id(), quantity, Integer::sum);
            totalWeight = totalWeight.add(item.getWeightTon());
        }
        entity.getItems().sort(java.util.Comparator.comparing(FreightBillItem::getLineNo));
        entity.setTotalWeight(TradeItemCalculator.scaleWeightTon(totalWeight));
        entity.setTotalFreight(totalWeight.multiply(request.unitPrice())
                .setScale(PrecisionConstants.AMOUNT_SCALE, PrecisionConstants.DEFAULT_ROUNDING));
        syncSourceItems(entity, appliedQuantityByItemId, nextId);
        syncSourceOrders(entity, sourceSnapshot.orders(), nextId);
    }

    private int resolveQuantity(FreightBillItemRequest request,
                                SalesOrderSourceItemSnapshot source,
                                SourceSnapshot sourceSnapshot,
                                int lineNo) {
        int requested = request.quantity() != null ? request.quantity() : source.quantity();
        if (requested <= 0) {
            throw business("第" + lineNo + "行数量必须大于0");
        }
        if (requested > source.quantity()) {
            throw business("第" + lineNo + "行数量超过来源销售订单明细数量");
        }
        int occupied = sourceSnapshot.occupiedQuantityByItemId().getOrDefault(source.id(), 0);
        int remaining = Math.max(source.quantity() - occupied, 0);
        if (requested > remaining) {
            throw business("第" + lineNo + "行可导入数量不足，剩余可用 " + remaining + " 件");
        }
        return requested;
    }

    private void applyItem(FreightBill entity,
                           FreightBillItem item,
                           FreightBillItemRequest request,
                           SalesOrderSourceItemSnapshot source,
                           SalesOrderSourceSnapshot sourceOrder,
                           int quantity,
                           int lineNo) {
        validateFixedFields(request, source, sourceOrder, lineNo);
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
        item.setQuantity(quantity);
        item.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()));
        item.setPieceWeightTon(TradeItemCalculator.scaleWeightTon(source.pieceWeightTon()));
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setBatchNo(source.batchNo());
        item.setWeightTon(resolveWeightTon(request, source, quantity, lineNo));
        item.setWarehouseId(source.warehouseId());
        item.setWarehouseName(source.warehouseName());
    }

    private BigDecimal resolveWeightTon(FreightBillItemRequest request,
                                        SalesOrderSourceItemSnapshot source,
                                        int quantity,
                                        int lineNo) {
        BigDecimal requestedWeight = request.weightTon();
        if (requestedWeight != null && requestedWeight.signum() > 0) {
            return TradeItemCalculator.scaleWeightTon(requestedWeight);
        }
        BigDecimal pieceWeightTon = TradeItemCalculator.scaleWeightTon(source.pieceWeightTon());
        if (pieceWeightTon.signum() <= 0) {
            throw business("第" + lineNo + "行来源明细件重缺失，换算重量小于等于0");
        }
        return TradeItemCalculator.calculateWeightTon(quantity, pieceWeightTon);
    }

    private void validateFixedFields(FreightBillItemRequest request,
                                     SalesOrderSourceItemSnapshot source,
                                     SalesOrderSourceSnapshot sourceOrder,
                                     int lineNo) {
        if (request.materialId() != null && !Objects.equals(request.materialId(), source.materialId())) {
            throw business("第" + lineNo + "行商品ID与来源不一致");
        }
        requireSameOptionalText(request.materialCode(), source.materialCode(), lineNo, "物料编码");
        requireSameOptionalText(request.brand(), source.brand(), lineNo, "品牌");
        requireSameOptionalText(request.category(), source.category(), lineNo, "品类");
        requireSameOptionalText(request.material(), source.material(), lineNo, "材质");
        requireSameOptionalText(request.spec(), source.spec(), lineNo, "规格");
        requireSameOptionalText(request.length(), source.length(), lineNo, "长度");
        if (source.quantityUnit() != null) {
            String requested = request.quantityUnit() == null
                    ? null
                    : TradeItemCalculator.normalizeQuantityUnit(request.quantityUnit());
            requireSameOptionalText(requested, TradeItemCalculator.normalizeQuantityUnit(source.quantityUnit()),
                    lineNo, "数量单位");
        }
        requireSameOptionalDecimal(request.pieceWeightTon(), source.pieceWeightTon(), lineNo, "件重");
        requireSameOptionalInteger(request.piecesPerBundle(), source.piecesPerBundle(), lineNo, "每捆支数");
        if (request.warehouseId() != null && !Objects.equals(request.warehouseId(), source.warehouseId())) {
            throw business("第" + lineNo + "行仓库ID与来源不一致");
        }
        requireSameOptionalText(request.warehouseName(), source.warehouseName(), lineNo, "仓库");
        requireSameOptionalText(request.batchNo(), source.batchNo(), lineNo, "批号");
        if (request.settlementCompanyId() != null
                && !Objects.equals(request.settlementCompanyId(), source.settlementCompanyId())) {
            throw business("第" + lineNo + "行结算主体ID与来源不一致");
        }
        if (request.customerId() != null && !Objects.equals(request.customerId(), sourceOrder.customerId())) {
            throw business("第" + lineNo + "行客户ID与来源不一致");
        }
        if (request.projectId() != null && !Objects.equals(request.projectId(), sourceOrder.projectId())) {
            throw business("第" + lineNo + "行项目ID与来源不一致");
        }
    }

    private void requireSameOptionalText(String requested, String source, int lineNo, String fieldName) {
        if (requested == null || source == null) {
            return;
        }
        BusinessDocumentValidator.requireSameSourceText(requested, source, lineNo, "来源明细", fieldName);
    }

    private void requireSameOptionalInteger(Integer requested, Integer source, int lineNo, String fieldName) {
        if (requested == null || source == null) {
            return;
        }
        BusinessDocumentValidator.requireSameSourceInteger(requested, source, lineNo, "来源明细", fieldName);
    }

    private void requireSameOptionalDecimal(BigDecimal requested, BigDecimal source, int lineNo, String fieldName) {
        if (requested == null || source == null) {
            return;
        }
        BusinessDocumentValidator.requireSameSourceDecimal(requested, source, lineNo, "来源明细", fieldName);
    }

    private SourceSnapshot resolveSources(FreightBill entity, List<FreightBillItemRequest> requestedItems) {
        LinkedHashSet<Long> requestedItemIds = requestedItems.stream()
                .map(FreightBillItemRequest::sourceSalesOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedItemIds.size() != requestedItems.size()) {
            throw business("物流单来源销售订单明细ID不能为空或重复");
        }
        // 加锁前只投影父订单主键, 不加载订单实体: 否则加锁后重查会命中一级缓存的旧快照,
        // 来源订单并发反审核时可能读到过期状态(TOCTOU)。
        LinkedHashSet<Long> sourceOrderIds = new LinkedHashSet<>(
                salesOrderSourceQuery.findOrderIdsBySourceItemIds(requestedItemIds));
        sourceAllocationLockService.lockDocumentSources(List.of(), sourceOrderIds, List.of(), List.of());
        List<SalesOrderSourceSnapshot> orders = salesOrderSourceQuery.findBySourceItemIds(requestedItemIds);
        validateOrders(orders);
        Map<Long, SalesOrderSourceItemSnapshot> itemById = new LinkedHashMap<>();
        Map<Long, SalesOrderSourceSnapshot> orderByItemId = new LinkedHashMap<>();
        for (SalesOrderSourceSnapshot order : orders) {
            for (SalesOrderSourceItemSnapshot item : order.items()) {
                itemById.putIfAbsent(item.id(), item);
                orderByItemId.putIfAbsent(item.id(), order);
            }
        }
        // 请求明细必须是来源销售订单明细的子集：允许部分导入，但不允许引用不存在的来源行。
        for (Long requestedItemId : requestedItemIds) {
            if (!itemById.containsKey(requestedItemId)) {
                throw business("来源销售订单明细不存在或不属于所选销售订单");
            }
        }
        Map<Long, Integer> occupiedQuantityByItemId = loadOccupiedQuantities(entity, requestedItemIds);
        return new SourceSnapshot(orders, itemById, orderByItemId, occupiedQuantityByItemId);
    }

    private Map<Long, Integer> loadOccupiedQuantities(FreightBill entity, Set<Long> sourceItemIds) {
        Map<Long, Integer> occupied = new LinkedHashMap<>();
        List<FreightBillSourceItemRepository.SourceItemOccupancySummary> summaries =
                sourceItemRepository.summarizeOccupiedQuantities(sourceItemIds, entity.getId());
        for (FreightBillSourceItemRepository.SourceItemOccupancySummary summary : summaries) {
            occupied.put(summary.getSourceSalesOrderItemId(), Math.toIntExact(summary.getTotalQuantity()));
        }
        return occupied;
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

    /**
     * 按来源销售订单明细 diff 维护行级占用：新增/更新占用数量，移除的来源行置为非活跃以释放额度。
     * 已存在的占用行复用（重新激活）而非新建，避免 (物流单, 来源明细) 唯一约束冲突。
     */
    private void syncSourceItems(FreightBill entity,
                                 Map<Long, Integer> quantityByItemId,
                                 LongSupplier nextId) {
        // 保留非活跃历史占用行（唯一约束不区分 active），重新引用时复用并重新激活，避免重复插入冲突。
        Map<Long, FreightBillSourceItem> existingByItemId = entity.getSourceItems().stream()
                .collect(Collectors.toMap(FreightBillSourceItem::getSourceSalesOrderItemId, item -> item,
                        (left, right) -> left));
        for (Map.Entry<Long, Integer> entry : quantityByItemId.entrySet()) {
            FreightBillSourceItem relation = existingByItemId.get(entry.getKey());
            if (relation == null) {
                relation = new FreightBillSourceItem();
                relation.setId(nextId.getAsLong());
                relation.setFreightBill(entity);
                relation.setSourceSalesOrderItemId(entry.getKey());
                entity.getSourceItems().add(relation);
            }
            relation.setQuantity(entry.getValue());
            relation.setActiveFlag(true);
        }
        for (FreightBillSourceItem existing : entity.getSourceItems()) {
            if (!quantityByItemId.containsKey(existing.getSourceSalesOrderItemId())) {
                existing.setActiveFlag(false);
            }
        }
    }

    private void syncSourceOrders(FreightBill entity,
                                  List<SalesOrderSourceSnapshot> orders,
                                  LongSupplier nextId) {
        Set<Long> requestedOrderIds = orders.stream()
                .map(SalesOrderSourceSnapshot::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> existingOrderIds = entity.getSourceOrders().stream()
                .map(FreightBillSourceOrder::getSourceSalesOrderId)
                .collect(Collectors.toSet());
        for (SalesOrderSourceSnapshot order : orders) {
            if (existingOrderIds.contains(order.id())) {
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
        entity.getSourceOrders().forEach(relation ->
                relation.setActiveFlag(requestedOrderIds.contains(relation.getSourceSalesOrderId())));
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    private record SourceSnapshot(
            List<SalesOrderSourceSnapshot> orders,
            Map<Long, SalesOrderSourceItemSnapshot> itemById,
            Map<Long, SalesOrderSourceSnapshot> orderByItemId,
            Map<Long, Integer> occupiedQuantityByItemId
    ) {
    }
}
