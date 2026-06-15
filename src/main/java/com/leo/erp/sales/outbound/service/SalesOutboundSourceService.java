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
                                      String headerCustomerName,
                                      String headerProjectName,
                                      String warehouseName,
                                      String batchNo,
                                      Map<Long, Integer> requestSourceQuantityMap,
                                      int lineNo) {
        if (sourceSalesOrderItemId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第" + lineNo + "行来源销售订单明细不能为空");
        }
        var sourceSalesOrder = sourceSalesOrderItem.getSalesOrder();
        String sourceStatus = sourceSalesOrder == null ? null : sourceSalesOrder.getStatus();
        BusinessDocumentValidator.requireStatusIn(
                sourceStatus,
                java.util.Set.of(StatusConstants.AUDITED),
                "第" + lineNo + "行来源销售订单未审核，不能作为来源单据"
        );
        assertSameOrderText(headerCustomerName, sourceSalesOrder == null ? null : sourceSalesOrder.getCustomerName(), lineNo, "客户");
        assertSameOrderText(headerProjectName, sourceSalesOrder == null ? null : sourceSalesOrder.getProjectName(), lineNo, "项目");
        BusinessDocumentValidator.requireSameSourceText(request.materialCode(), sourceSalesOrderItem.getMaterialCode(), lineNo, "来源销售订单明细", "物料编码");
        BusinessDocumentValidator.requireSameSourceText(request.brand(), sourceSalesOrderItem.getBrand(), lineNo, "来源销售订单明细", "品牌");
        BusinessDocumentValidator.requireSameSourceText(request.category(), sourceSalesOrderItem.getCategory(), lineNo, "来源销售订单明细", "品类");
        BusinessDocumentValidator.requireSameSourceText(request.material(), sourceSalesOrderItem.getMaterial(), lineNo, "来源销售订单明细", "材质");
        BusinessDocumentValidator.requireSameSourceText(request.spec(), sourceSalesOrderItem.getSpec(), lineNo, "来源销售订单明细", "规格");
        BusinessDocumentValidator.requireSameSourceText(request.unit(), sourceSalesOrderItem.getUnit(), lineNo, "来源销售订单明细", "单位");
        BusinessDocumentValidator.requireSameSourceText(warehouseName, sourceSalesOrderItem.getWarehouseName(), lineNo, "来源销售订单明细", "仓库");
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

    void assertSourceSalesOrderItemsNotOccupied(
            Collection<Long> sourceSalesOrderItemIds,
            Long currentOutboundId
    ) {
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
}
