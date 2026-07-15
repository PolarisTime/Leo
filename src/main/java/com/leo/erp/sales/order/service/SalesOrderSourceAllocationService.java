package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService.SourceInboundItemRecord;
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
import java.util.Set;

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
        validateSourceShape(request);
        List<Long> sourceInboundItemIds = extractSourceInboundItemIds(request);
        return new SalesOrderSourceContext(
                sourceInboundItemIds,
                loadSourceInboundItemMap(sourceInboundItemIds),
                loadInboundAllocatedMap(sourceInboundItemIds, currentOrderId),
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

    void validateLine(SalesOrderItemRequest source,
                      int lineNo,
                      SalesOrderSourceContext context) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (source.sourceInboundItemId() != null && sourcePurchaseOrderItemId != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细和来源采购订单明细不能同时填写");
        }
        if (sourcePurchaseOrderItemId != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单直连来源已停用，销售订单只能导入已完成采购的入库明细");
        }

        Long sourceInboundItemId = source.sourceInboundItemId();
        if (sourceInboundItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行必须导入上级采购来源明细");
        }
        SourceInboundItemRecord sourceInboundItem = context.sourceInboundItemMap().get(sourceInboundItemId);
        if (sourceInboundItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购入库明细不存在");
        }
        assertSourceParentStatus(
                sourceInboundItem.inboundStatus(),
                Set.of(StatusConstants.AUDITED, StatusConstants.INBOUND_COMPLETED),
                lineNo,
                "来源采购入库单"
        );
        if (!StatusConstants.PURCHASE_COMPLETED.equals(sourceInboundItem.purchaseOrderStatus())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源采购订单尚未完成采购，不能用于正常销售"
            );
        }
        assertSourceFieldsMatch(source, sourceInboundItem, lineNo, "来源采购入库明细");
        int allocatedQuantity = context.inboundAllocatedMap()
                .getOrDefault(sourceInboundItemId, SalesOrderSourceAllocation.ZERO)
                .quantity();
        int requestedQuantity = context.requestInboundAllocatedMap()
                .getOrDefault(sourceInboundItemId, SalesOrderSourceAllocation.ZERO)
                .quantity();
        validateAvailableQuantity(source.quantity(), sourceInboundItem.quantity(), allocatedQuantity, requestedQuantity, lineNo);
    }

    private void validateSourceShape(SalesOrderRequest request) {
        Set<Long> inboundSourceIds = new LinkedHashSet<>();
        for (int index = 0; index < request.items().size(); index++) {
            SalesOrderItemRequest item = request.items().get(index);
            boolean inboundSource = item.sourceInboundItemId() != null;
            boolean purchaseOrderSource = item.sourcePurchaseOrderItemId() != null;
            if (inboundSource == purchaseOrderSource) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + (index + 1) + "行必须且只能选择一个采购来源明细"
                );
            }
            if (!inboundSource) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "采购订单直连来源已停用，销售订单只能导入已完成采购的入库明细");
            }
            if (inboundSource && !inboundSourceIds.add(item.sourceInboundItemId())) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "第" + (index + 1) + "行重复导入同一采购入库明细"
                );
            }
        }
    }

    void recordAllocation(SalesOrderItemRequest source, BigDecimal weightTon, SalesOrderSourceContext context) {
        if (source.sourceInboundItemId() != null) {
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

    private Map<Long, SourceInboundItemRecord> loadSourceInboundItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        return purchaseItemQueryAppService.findSourceInboundItemsByIds(sourceIds).stream()
                .collect(java.util.stream.Collectors.toMap(SourceInboundItemRecord::id, item -> item));
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

    private void assertSourceParentStatus(
            String actualStatus,
            Set<String> allowedStatuses,
            int lineNo,
            String sourceName
    ) {
        BusinessDocumentValidator.requireStatusIn(
                actualStatus,
                allowedStatuses,
                "第" + lineNo + "行" + sourceName + "未审核，不能作为来源单据"
        );
    }

    private void assertSourceFieldsMatch(SalesOrderItemRequest request,
                                         SourceInboundItemRecord source,
                                         int lineNo,
                                         String sourceName) {
        assertSameText(request.materialCode(), source.materialCode(), lineNo, sourceName, "物料编码");
        assertSameId(request.materialId(), source.materialId(), lineNo, sourceName, "商品ID");
        assertSameText(request.brand(), source.brand(), lineNo, sourceName, "品牌");
        assertSameText(request.category(), source.category(), lineNo, sourceName, "品类");
        assertSameText(request.material(), source.material(), lineNo, sourceName, "材质");
        assertSameText(request.spec(), source.spec(), lineNo, sourceName, "规格");
        assertSameOptionalText(request.length(), source.length(), lineNo, sourceName, "长度");
        assertSameText(request.unit(), source.unit(), lineNo, sourceName, "单位");
        assertSameOptionalQuantityUnit(request.quantityUnit(), source.quantityUnit(), lineNo, sourceName);
        assertSameOptionalDecimal(request.pieceWeightTon(), source.pieceWeightTon(), lineNo, sourceName, "件重");
        assertSameOptionalInteger(request.piecesPerBundle(), source.piecesPerBundle(), lineNo, sourceName, "每捆支数");
        assertSameText(request.warehouseName(), source.warehouseName(), lineNo, sourceName, "仓库");
        assertSameId(request.warehouseId(), source.warehouseId(), lineNo, sourceName, "仓库ID");
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

    private void assertSameId(Long requestedValue,
                              Long sourceValue,
                              int lineNo,
                              String sourceName,
                              String fieldName) {
        if (requestedValue != null && !java.util.Objects.equals(requestedValue, sourceValue)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行" + sourceName + fieldName + "不一致"
            );
        }
    }

    private void assertSameOptionalText(String requestedValue,
                                        String sourceValue,
                                        int lineNo,
                                        String sourceName,
                                        String fieldName) {
        if (sourceValue != null) {
            assertSameText(requestedValue, sourceValue, lineNo, sourceName, fieldName);
        }
    }

    private void assertSameOptionalQuantityUnit(String requestedValue,
                                                String sourceValue,
                                                int lineNo,
                                                String sourceName) {
        if (sourceValue != null) {
            assertSameText(
                    TradeItemCalculator.normalizeQuantityUnit(requestedValue),
                    TradeItemCalculator.normalizeQuantityUnit(sourceValue),
                    lineNo,
                    sourceName,
                    "数量单位"
            );
        }
    }

    private void assertSameOptionalInteger(Integer requestedValue,
                                           Integer sourceValue,
                                           int lineNo,
                                           String sourceName,
                                           String fieldName) {
        if (sourceValue != null) {
            BusinessDocumentValidator.requireSameSourceInteger(
                    requestedValue,
                    sourceValue,
                    lineNo,
                    sourceName,
                    fieldName
            );
        }
    }

    private void assertSameOptionalDecimal(BigDecimal requestedValue,
                                           BigDecimal sourceValue,
                                           int lineNo,
                                           String sourceName,
                                           String fieldName) {
        if (sourceValue != null) {
            BusinessDocumentValidator.requireSameSourceDecimal(
                    requestedValue,
                    sourceValue,
                    lineNo,
                    sourceName,
                    fieldName
            );
        }
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
