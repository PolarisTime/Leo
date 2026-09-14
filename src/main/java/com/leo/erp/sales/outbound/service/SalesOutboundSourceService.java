package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class SalesOutboundSourceService {

    private final SalesOrderItemQueryService salesOrderItemQueryService;
    private final SalesOutboundRepository repository;

    public SalesOutboundSourceService(SalesOrderItemQueryService salesOrderItemQueryService,
                                      SalesOutboundRepository repository) {
        this.salesOrderItemQueryService = salesOrderItemQueryService;
        this.repository = repository;
    }

    Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<SalesOutboundItemRequest> requestItems,
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
        return loadSourceSalesOrderItemMapByIds(sourceSalesOrderItemIds);
    }

    Map<Long, SalesOrderItem> loadSourceSalesOrderItemMap(List<SalesOutboundItem> items) {
        List<Long> sourceSalesOrderItemIds = items.stream()
                .map(SalesOutboundItem::getSourceSalesOrderItemId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        return loadSourceSalesOrderItemMapByIds(sourceSalesOrderItemIds);
    }

    Long resolveSourceSalesOrderItemId(SalesOutboundItemRequest source, SalesOutboundItem item, int lineNo) {
        if (source.sourceSalesOrderItemId() != null) {
            return source.sourceSalesOrderItemId();
        }
        Long persistedSourceSalesOrderItemId = item.getSourceSalesOrderItemId();
        if (persistedSourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }
        return persistedSourceSalesOrderItemId;
    }

    SalesOrderItem resolveSourceSalesOrderItem(Map<Long, SalesOrderItem> sourceSalesOrderItemMap,
                                               Long sourceSalesOrderItemId,
                                               int lineNo) {
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }
        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(sourceSalesOrderItemId);
        if (sourceSalesOrderItem == null || sourceSalesOrderItem.getSalesOrder() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不存在");
        }
        return sourceSalesOrderItem;
    }

    void validateSourceSalesOrderItem(SalesOutboundItemRequest request,
                                      SalesOrderItem sourceSalesOrderItem,
                                      Long sourceSalesOrderItemId,
                                      Long headerCustomerId,
                                      String headerCustomerName,
                                      Long headerProjectId,
                                      String headerProjectName,
                                      Long warehouseId,
                                      String warehouseName,
                                      String batchNo,
                                      Map<Long, Integer> requestSourceQuantityMap,
                                      int lineNo) {
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }
        var sourceSalesOrder = sourceSalesOrderItem.getSalesOrder();
        if (sourceSalesOrder == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单不存在");
        }
        String sourceStatus = sourceSalesOrder.getStatus();
        BusinessDocumentValidator.requireStatusIn(
                sourceStatus,
                java.util.Set.of(StatusConstants.AUDITED),
                "第" + lineNo + "行来源销售订单未审核，不能作为来源单据"
        );
        assertSameId(headerCustomerId, sourceSalesOrder.getCustomerId(), lineNo, "客户ID");
        assertSameOrderText(headerCustomerName, sourceSalesOrder.getCustomerName(), lineNo, "客户");
        assertSameId(headerProjectId, sourceSalesOrder.getProjectId(), lineNo, "项目ID");
        assertSameOrderText(headerProjectName, sourceSalesOrder.getProjectName(), lineNo, "项目");
        assertSameId(request.materialId(), sourceSalesOrderItem.getMaterialId(), lineNo, "商品ID");
        // 前端精简保存 payload 后不发送材料类字段（后端按来源明细重载），请求显式提供时才比对一致性。
        BusinessDocumentValidator.requireSameOptionalSourceText(request.materialCode(), sourceSalesOrderItem.getMaterialCode(), lineNo, "来源销售订单明细", "物料编码");
        BusinessDocumentValidator.requireSameOptionalSourceText(request.brand(), sourceSalesOrderItem.getBrand(), lineNo, "来源销售订单明细", "品牌");
        BusinessDocumentValidator.requireSameOptionalSourceText(request.category(), sourceSalesOrderItem.getCategory(), lineNo, "来源销售订单明细", "品类");
        BusinessDocumentValidator.requireSameOptionalSourceText(request.material(), sourceSalesOrderItem.getMaterial(), lineNo, "来源销售订单明细", "材质");
        BusinessDocumentValidator.requireSameOptionalSourceText(request.spec(), sourceSalesOrderItem.getSpec(), lineNo, "来源销售订单明细", "规格");
        BusinessDocumentValidator.requireSameOptionalSourceText(request.unit(), sourceSalesOrderItem.getUnit(), lineNo, "来源销售订单明细", "单位");
        assertSameId(warehouseId, sourceSalesOrderItem.getWarehouseId(), lineNo, "仓库ID");
        BusinessDocumentValidator.requireSameOptionalSourceText(warehouseName, sourceSalesOrderItem.getWarehouseName(), lineNo, "来源销售订单明细", "仓库");
        BusinessDocumentValidator.requireSameSourceText(batchNo, sourceSalesOrderItem.getBatchNo(), lineNo, "来源销售订单明细", "批号");

        int currentQuantity = request.quantity() == null ? 0 : request.quantity();
        int requestedQuantity = requestSourceQuantityMap.getOrDefault(sourceSalesOrderItemId, 0);
        int sourceQuantity = sourceSalesOrderItem.getQuantity() == null ? 0 : sourceSalesOrderItem.getQuantity();
        if (requestedQuantity + currentQuantity > sourceQuantity) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行来源销售订单明细可出库数量不足，剩余可用 " + Math.max(sourceQuantity - requestedQuantity, 0) + " 件"
            );
        }
        requestSourceQuantityMap.put(sourceSalesOrderItemId, requestedQuantity + currentQuantity);
    }

    void validateSourceSalesOrderItem(SalesOutboundItemRequest request,
                                      SalesOrderItem sourceSalesOrderItem,
                                      Long sourceSalesOrderItemId,
                                      String headerCustomerName,
                                      String headerProjectName,
                                      String warehouseName,
                                      String batchNo,
                                      Map<Long, Integer> requestSourceQuantityMap,
                                      int lineNo) {
        validateSourceSalesOrderItem(
                request,
                sourceSalesOrderItem,
                sourceSalesOrderItemId,
                null,
                headerCustomerName,
                null,
                headerProjectName,
                null,
                warehouseName,
                batchNo,
                requestSourceQuantityMap,
                lineNo
        );
    }

    /**
     * 汇总其它未删除销售出库对来源销售订单明细的已用数量。
     * 用于累计覆盖校验：允许同一来源明细被多张出库引用，但累计数量不得超过订单明细数量。
     * 更新单张出库时通过 {@code currentOutboundId} 排除自身。
     */
    Map<Long, Integer> sumOtherOutboundQuantitiesBySourceSalesOrderItemIds(
            Collection<Long> sourceSalesOrderItemIds,
            Long currentOutboundId
    ) {
        Map<Long, Integer> summary = new java.util.LinkedHashMap<>();
        if (sourceSalesOrderItemIds == null || sourceSalesOrderItemIds.isEmpty()) {
            return summary;
        }
        Collection<Long> requestedIds = sourceSalesOrderItemIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (requestedIds.isEmpty()) {
            return summary;
        }

        List<SalesOutbound> otherOutbounds =
                repository.findAllBySourceSalesOrderItemIdsExcludingCurrentOutbound(
                        requestedIds,
                        currentOutboundId
                );
        for (SalesOutbound otherOutbound : otherOutbounds) {
            if (otherOutbound.getItems() == null) {
                continue;
            }
            for (SalesOutboundItem item : otherOutbound.getItems()) {
                Long sourceSalesOrderItemId = item.getSourceSalesOrderItemId();
                if (sourceSalesOrderItemId != null && requestedIds.contains(sourceSalesOrderItemId)) {
                    summary.merge(
                            sourceSalesOrderItemId,
                            item.getQuantity() == null ? 0 : item.getQuantity(),
                            Integer::sum
                    );
                }
            }
        }
        return summary;
    }

    void collectSourceSalesOrderNos(LinkedHashSet<String> sourceSalesOrderNos,
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
        String sourceNo = BusinessDocumentValidator.trimToNull(source.sourceNo());
        if (sourceNo != null) {
            sourceSalesOrderNos.add(sourceNo);
        }
    }

    String resolveItemSourceNo(SalesOutboundItem item, Map<Long, SalesOrderItem> sourceSalesOrderItemMap) {
        if (item.getSourceSalesOrderItemId() == null) {
            return null;
        }
        SalesOrderItem sourceSalesOrderItem = sourceSalesOrderItemMap.get(item.getSourceSalesOrderItemId());
        if (sourceSalesOrderItem == null || sourceSalesOrderItem.getSalesOrder() == null) {
            return null;
        }
        return sourceSalesOrderItem.getSalesOrder().getOrderNo();
    }

    private Map<Long, SalesOrderItem> loadSourceSalesOrderItemMapByIds(Collection<Long> sourceSalesOrderItemIds) {
        if (sourceSalesOrderItemIds.isEmpty()) {
            return Map.of();
        }
        return salesOrderItemQueryService.findActiveByIdIn(sourceSalesOrderItemIds).stream()
                .collect(java.util.stream.Collectors.toMap(SalesOrderItem::getId, item -> item));
    }

    private void assertSameOrderText(String requestedValue, String sourceValue, int lineNo, String fieldName) {
        BusinessDocumentValidator.requireSameSourceText(
                requestedValue,
                sourceValue,
                lineNo,
                "来源销售订单",
                fieldName
        );
    }

    private void assertSameId(Long requestedValue, Long sourceValue, int lineNo, String fieldName) {
        if (requestedValue != null && !java.util.Objects.equals(requestedValue, sourceValue)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "第" + lineNo + "行" + fieldName + "与来源销售订单不一致"
            );
        }
    }
}
