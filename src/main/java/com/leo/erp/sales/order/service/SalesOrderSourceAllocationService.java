package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class SalesOrderSourceAllocationService {

    private final PurchaseItemQueryAppService purchaseItemQueryAppService;
    private final SalesOrderItemRepository salesOrderItemRepository;

    public SalesOrderSourceAllocationService(PurchaseItemQueryAppService purchaseItemQueryAppService,
                                             SalesOrderItemRepository salesOrderItemRepository) {
        this.purchaseItemQueryAppService = purchaseItemQueryAppService;
        this.salesOrderItemRepository = salesOrderItemRepository;
    }

    SalesOrderSourceContext prepareContext(SalesOrderRequest request, Long currentOrderId) {
        List<Long> sourceInboundItemIds = extractSourceInboundItemIds(request);
        List<Long> sourcePurchaseOrderItemIds = extractSourcePurchaseOrderItemIds(request);
        return new SalesOrderSourceContext(
                sourceInboundItemIds,
                sourcePurchaseOrderItemIds,
                loadSourceInboundItemMap(sourceInboundItemIds),
                loadSourcePurchaseOrderItemMap(sourcePurchaseOrderItemIds),
                loadInboundAllocatedMap(sourceInboundItemIds, currentOrderId),
                loadPurchaseOrderAllocatedMap(sourcePurchaseOrderItemIds, currentOrderId),
                Map.of(),
                new HashMap<>(),
                new HashMap<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>()
        );
    }

    SourceInboundItemRecord resolveSourceInbound(SalesOrderItemRequest source, SalesOrderSourceContext context) {
        SourceInboundItemRecord sourceInboundItem = source.sourceInboundItemId() == null ? null
                : context.sourceInboundItemMap().get(source.sourceInboundItemId());
        if (sourceInboundItem != null && sourceInboundItem.inboundNo() != null) {
            context.sourceInboundNos().add(sourceInboundItem.inboundNo());
            if (sourceInboundItem.purchaseOrderNo() != null) {
                context.sourcePurchaseOrderNos().add(sourceInboundItem.purchaseOrderNo());
            }
        }
        return sourceInboundItem;
    }

    SourcePurchaseOrderItemRecord resolveSourcePurchaseOrder(SalesOrderItemRequest source, SalesOrderSourceContext context) {
        SourcePurchaseOrderItemRecord sourcePurchaseOrderItem = source.sourcePurchaseOrderItemId() == null ? null
                : context.sourcePurchaseOrderItemMap().get(source.sourcePurchaseOrderItemId());
        if (sourcePurchaseOrderItem != null && sourcePurchaseOrderItem.orderNo() != null) {
            context.sourcePurchaseOrderNos().add(sourcePurchaseOrderItem.orderNo());
        }
        return sourcePurchaseOrderItem;
    }

    void validateLine(SalesOrderItemRequest source, int lineNo, SalesOrderSourceContext context) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (source.sourceInboundItemId() != null && sourcePurchaseOrderItemId != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细和来源采购订单明细不能同时填写");
        }
        if (sourcePurchaseOrderItemId != null) {
            SourcePurchaseOrderItemRecord sourcePurchaseOrderItem = context.sourcePurchaseOrderItemMap().get(sourcePurchaseOrderItemId);
            if (sourcePurchaseOrderItem == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
            }
            assertSourceParentStatus(sourcePurchaseOrderItem.orderStatus(), StatusConstants.AUDITED, lineNo, "来源采购订单");
            assertSourceFieldsMatch(source, sourcePurchaseOrderItem, lineNo, "来源采购订单明细");
            int allocatedQuantity = context.purchaseOrderAllocatedMap()
                    .getOrDefault(sourcePurchaseOrderItemId, SalesOrderSourceAllocation.ZERO)
                    .quantity();
            int requestedQuantity = context.requestPurchaseOrderAllocatedMap()
                    .getOrDefault(sourcePurchaseOrderItemId, SalesOrderSourceAllocation.ZERO)
                    .quantity();
            validateAvailableQuantity(source.quantity(), sourcePurchaseOrderItem.quantity(), allocatedQuantity, requestedQuantity, lineNo);
            return;
        }

        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null) {
            return;
        }
        SourceInboundItemRecord sourceInboundItem = context.sourceInboundItemMap().get(sourceInboundItemId);
        if (sourceInboundItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不存在");
        }
        assertSourceParentStatus(sourceInboundItem.inboundStatus(), StatusConstants.AUDITED, lineNo, "来源采购入库单");
        assertSourceFieldsMatch(source, sourceInboundItem, lineNo, "来源采购入库明细");
        int allocatedQuantity = context.inboundAllocatedMap()
                .getOrDefault(sourceInboundItemId, SalesOrderSourceAllocation.ZERO)
                .quantity();
        int requestedQuantity = context.requestInboundAllocatedMap()
                .getOrDefault(sourceInboundItemId, SalesOrderSourceAllocation.ZERO)
                .quantity();
        validateAvailableQuantity(source.quantity(), sourceInboundItem.quantity(), allocatedQuantity, requestedQuantity, lineNo);
    }

    void recordAllocation(SalesOrderItemRequest source, BigDecimal weightTon, SalesOrderSourceContext context) {
        if (source.sourcePurchaseOrderItemId() != null) {
            context.requestPurchaseOrderAllocatedMap().merge(
                    source.sourcePurchaseOrderItemId(),
                    new SalesOrderSourceAllocation(source.quantity(), weightTon),
                    SalesOrderSourceAllocation::merge
            );
        } else if (source.sourceInboundItemId() != null) {
            context.requestInboundAllocatedMap().merge(
                    source.sourceInboundItemId(),
                    new SalesOrderSourceAllocation(source.quantity(), weightTon),
                    SalesOrderSourceAllocation::merge
            );
        }
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

    private Map<Long, SalesOrderSourceAllocation> loadInboundAllocatedMap(List<Long> sourceInboundItemIds, Long currentOrderId) {
        if (sourceInboundItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SalesOrderSourceAllocation> allocatedMap = new HashMap<>();
        salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(sourceInboundItemIds, currentOrderId)
                .forEach(summary -> allocatedMap.put(
                        summary.getSourceInboundItemId(),
                        new SalesOrderSourceAllocation(
                                Math.toIntExact(summary.getTotalQuantity()),
                                TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                        )
                ));
        return allocatedMap;
    }

    private Map<Long, SalesOrderSourceAllocation> loadPurchaseOrderAllocatedMap(List<Long> sourcePurchaseOrderItemIds, Long currentOrderId) {
        if (sourcePurchaseOrderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SalesOrderSourceAllocation> allocatedMap = new HashMap<>();
        salesOrderItemRepository.summarizeAllocatedQuantityBySourcePurchaseOrderItemIds(sourcePurchaseOrderItemIds, currentOrderId)
                .forEach(summary -> allocatedMap.put(
                        summary.getSourcePurchaseOrderItemId(),
                        new SalesOrderSourceAllocation(
                                Math.toIntExact(summary.getTotalQuantity()),
                                TradeItemCalculator.scaleWeightTon(summary.getTotalWeightTon())
                        )
                ));
        return allocatedMap;
    }

    private void assertSourceParentStatus(String actualStatus, String requiredStatus, int lineNo, String sourceName) {
        BusinessDocumentValidator.requireStatusIn(
                actualStatus,
                java.util.Set.of(requiredStatus),
                "第" + lineNo + "行" + sourceName + "未审核，不能作为来源单据"
        );
    }

    private void assertSourceFieldsMatch(SalesOrderItemRequest request,
                                         SourceInboundItemRecord source,
                                         int lineNo,
                                         String sourceName) {
        assertSameText(request.materialCode(), source.materialCode(), lineNo, sourceName, "物料编码");
        assertSameText(request.brand(), source.brand(), lineNo, sourceName, "品牌");
        assertSameText(request.category(), source.category(), lineNo, sourceName, "品类");
        assertSameText(request.material(), source.material(), lineNo, sourceName, "材质");
        assertSameText(request.spec(), source.spec(), lineNo, sourceName, "规格");
        assertSameText(request.unit(), source.unit(), lineNo, sourceName, "单位");
        assertSameText(request.warehouseName(), source.warehouseName(), lineNo, sourceName, "仓库");
        assertSameText(request.batchNo(), source.batchNo(), lineNo, sourceName, "批号");
    }

    private void assertSourceFieldsMatch(SalesOrderItemRequest request,
                                         SourcePurchaseOrderItemRecord source,
                                         int lineNo,
                                         String sourceName) {
        assertSameText(request.materialCode(), source.materialCode(), lineNo, sourceName, "物料编码");
        assertSameText(request.brand(), source.brand(), lineNo, sourceName, "品牌");
        assertSameText(request.category(), source.category(), lineNo, sourceName, "品类");
        assertSameText(request.material(), source.material(), lineNo, sourceName, "材质");
        assertSameText(request.spec(), source.spec(), lineNo, sourceName, "规格");
        assertSameText(request.unit(), source.unit(), lineNo, sourceName, "单位");
        assertSameText(request.warehouseName(), source.warehouseName(), lineNo, sourceName, "仓库");
        assertSameText(request.batchNo(), source.batchNo(), lineNo, sourceName, "批号");
    }

    private void assertSameText(String requestedValue,
                                String sourceValue,
                                int lineNo,
                                String sourceName,
                                String fieldName) {
        BusinessDocumentValidator.requireSameSourceText(
                requestedValue,
                sourceValue,
                lineNo,
                sourceName,
                fieldName
        );
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
}
