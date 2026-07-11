package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PurchaseInboundSourceValidator {

    private final PurchaseOrderItemQueryService purchaseOrderItemQueryService;
    private final PurchaseInboundAllocationService allocationService;

    @Autowired
    public PurchaseInboundSourceValidator(PurchaseOrderItemQueryService purchaseOrderItemQueryService,
                                          PurchaseInboundAllocationService allocationService) {
        this.purchaseOrderItemQueryService = purchaseOrderItemQueryService;
        this.allocationService = allocationService;
    }

    SourceValidationContext prepareContext(
            PurchaseInboundRequest request,
            Long currentInboundId,
            List<Long> previousSourcePurchaseOrderItemIds
    ) {
        List<Long> sourcePurchaseOrderItemIds = allocationService.extractSourcePurchaseOrderItemIds(request);
        List<Long> affectedSourcePurchaseOrderItemIds = Stream
                .concat(previousSourcePurchaseOrderItemIds.stream(), sourcePurchaseOrderItemIds.stream())
                .distinct()
                .toList();
        return new SourceValidationContext(
                sourcePurchaseOrderItemIds,
                affectedSourcePurchaseOrderItemIds,
                loadSourcePurchaseOrderItemMap(affectedSourcePurchaseOrderItemIds),
                allocationService.prepareContext(sourcePurchaseOrderItemIds, currentInboundId)
        );
    }

    Map<Long, PurchaseOrderItem> loadSourcePurchaseOrderItemMap(List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PurchaseOrderItem> sourceMap = purchaseOrderItemQueryService.findActiveByIdIn(sourceIds).stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));

        // Pre-load lazy purchaseOrder to avoid LazyInitializationException in downstream mappers.
        sourceMap.values().forEach(item -> {
            if (item.getPurchaseOrder() != null) {
                item.getPurchaseOrder().getOrderNo();
            }
        });
        return sourceMap;
    }

    Map<Long, Integer> loadAllocatedQuantityMap(List<Long> sourcePurchaseOrderItemIds, Long currentInboundId) {
        return allocationService.loadAllocatedQuantityMap(sourcePurchaseOrderItemIds, currentInboundId);
    }

    void validateLine(
            PurchaseInboundItemRequest source,
            int lineNo,
            PurchaseInboundRequest request,
            SourceValidationContext context
    ) {
        Long sourcePurchaseOrderItemId = source.sourcePurchaseOrderItemId();
        if (sourcePurchaseOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不能为空");
        }
        PurchaseOrderItem sourcePurchaseOrderItem = context.sourcePurchaseOrderItemMap().get(sourcePurchaseOrderItemId);
        if (sourcePurchaseOrderItem == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源采购订单明细不存在");
        }
        validateSourcePurchaseOrderItemFields(
                source,
                sourcePurchaseOrderItem,
                request.supplierCode(),
                request.supplierName(),
                request.warehouseName(),
                lineNo
        );
        allocationService.validateAvailableQuantity(source, sourcePurchaseOrderItem, lineNo, context.allocationContext());
    }

    private void validateSourcePurchaseOrderItemFields(PurchaseInboundItemRequest request,
                                                       PurchaseOrderItem sourceItem,
                                                       String headerSupplierCode,
                                                       String headerSupplierName,
                                                       String headerWarehouseName,
                                                       int lineNo) {
        PurchaseOrder sourceOrder = sourceItem.getPurchaseOrder();
        String sourceStatus = sourceOrder == null ? null : sourceOrder.getStatus();
        BusinessDocumentValidator.requireStatusIn(
                sourceStatus,
                Set.of(StatusConstants.AUDITED),
                "第" + lineNo + "行来源采购订单未审核，不能作为来源单据"
        );
        String requestedSupplierCode = BusinessDocumentValidator.trimToNull(headerSupplierCode);
        if (requestedSupplierCode != null) {
            assertSourceOrderText(requestedSupplierCode, sourceOrder.getSupplierCode(), lineNo, "供应商编码");
        }
        assertSourceOrderText(headerSupplierName, sourceOrder.getSupplierName(), lineNo, "供应商");
        assertSourceItemText(request.materialCode(), sourceItem.getMaterialCode(), lineNo, "物料编码");
        assertSourceItemText(request.brand(), sourceItem.getBrand(), lineNo, "品牌");
        assertSourceItemText(request.category(), sourceItem.getCategory(), lineNo, "品类");
        assertSourceItemText(request.material(), sourceItem.getMaterial(), lineNo, "材质");
        assertSourceItemText(request.spec(), sourceItem.getSpec(), lineNo, "规格");
        assertSourceItemText(request.length(), sourceItem.getLength(), lineNo, "长度");
        assertSourceItemText(request.unit(), sourceItem.getUnit(), lineNo, "单位");
        String requestedWarehouseName = BusinessDocumentValidator.trimToNull(request.warehouseName()) == null
                ? headerWarehouseName : request.warehouseName();
        assertSourceItemText(requestedWarehouseName, sourceItem.getWarehouseName(), lineNo, "仓库");
        assertSourceItemText(request.batchNo(), sourceItem.getBatchNo(), lineNo, "批号");
        assertSourceItemText(
                TradeItemCalculator.normalizeQuantityUnit(request.quantityUnit()),
                TradeItemCalculator.normalizeQuantityUnit(sourceItem.getQuantityUnit()),
                lineNo,
                "数量单位"
        );
        assertSourceItemDecimal(request.pieceWeightTon(), sourceItem.getPieceWeightTon(), lineNo, "件重");
        assertSourceItemInteger(request.piecesPerBundle(), sourceItem.getPiecesPerBundle(), lineNo, "每捆支数");
        assertSourceItemDecimal(request.unitPrice(), sourceItem.getUnitPrice(), lineNo, "单价");
    }

    private void assertSourceOrderText(String requestedValue, String sourceValue, int lineNo, String fieldName) {
        BusinessDocumentValidator.requireSameSourceText(
                requestedValue,
                sourceValue,
                lineNo,
                "来源采购订单",
                fieldName
        );
    }

    private void assertSourceItemText(String requestedValue, String sourceValue, int lineNo, String fieldName) {
        BusinessDocumentValidator.requireSameSourceText(
                requestedValue,
                sourceValue,
                lineNo,
                "来源采购订单明细",
                fieldName
        );
    }

    private void assertSourceItemInteger(Integer requestedValue, Integer sourceValue, int lineNo, String fieldName) {
        BusinessDocumentValidator.requireSameSourceInteger(
                requestedValue,
                sourceValue,
                lineNo,
                "来源采购订单明细",
                fieldName
        );
    }

    private void assertSourceItemDecimal(BigDecimal requestedValue, BigDecimal sourceValue, int lineNo, String fieldName) {
        BusinessDocumentValidator.requireSameSourceDecimal(
                requestedValue,
                sourceValue,
                lineNo,
                "来源采购订单明细",
                fieldName
        );
    }

    record SourceValidationContext(
            List<Long> sourcePurchaseOrderItemIds,
            List<Long> affectedSourcePurchaseOrderItemIds,
            Map<Long, PurchaseOrderItem> sourcePurchaseOrderItemMap,
            PurchaseInboundAllocationService.AllocationContext allocationContext
    ) {

        Map<Long, Integer> allocatedQuantityMap() {
            return allocationContext.allocatedQuantityMap();
        }

        Map<Long, Integer> requestAllocatedQuantityMap() {
            return allocationContext.requestAllocatedQuantityMap();
        }
    }

    PurchaseInboundAllocationService allocationService() {
        return allocationService;
    }
}
