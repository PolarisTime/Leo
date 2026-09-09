package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售出库普通保存的请求规范化策略：状态只能通过审核/反审核变更，
 * 从销售订单导入的出库单只允许调整行重量等受限字段。
 */
@Service
public class SalesOutboundImportedUpdatePolicy {

    public SalesOutboundRequest normalizeUpdateRequest(SalesOutbound entity, SalesOutboundRequest request) {
        assertOrdinaryUpdateKeepsStatus(entity.getStatus(), request.status());
        if (hasImportedSalesOrder(entity)) {
            return restrictImportedOutboundUpdate(entity, request);
        }
        return new SalesOutboundRequest(
                entity.getOutboundNo(),
                entity.getSalesOrderNo(),
                request.customerId() == null ? entity.getCustomerId() : request.customerId(),
                request.customerName(),
                request.projectId() == null ? entity.getProjectId() : request.projectId(),
                request.projectName(),
                request.warehouseId() == null ? entity.getWarehouseId() : request.warehouseId(),
                request.warehouseName(),
                request.outboundDate(),
                entity.getStatus(),
                request.remark(),
                request.items(),
                request.audit()
        );
    }

    private void assertOrdinaryUpdateKeepsStatus(String currentStatus, String requestedStatus) {
        String normalizedRequestedStatus = requestedStatus == null ? null : requestedStatus.trim();
        if (normalizedRequestedStatus != null
                && !normalizedRequestedStatus.isEmpty()
                && !Objects.equals(currentStatus, normalizedRequestedStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "销售出库状态只能通过审核或反审核操作变更");
        }
    }

    private boolean hasImportedSalesOrder(SalesOutbound entity) {
        if (entity.getSalesOrderNo() != null && !entity.getSalesOrderNo().isBlank()) {
            return true;
        }
        return entity.getItems().stream()
                .anyMatch(item -> item.getSourceSalesOrderItemId() != null);
    }

    private SalesOutboundRequest restrictImportedOutboundUpdate(SalesOutbound entity, SalesOutboundRequest request) {
        Map<Long, SalesOutboundItemRequest> requestItemsById = request.items().stream()
                .filter(item -> item.id() != null)
                .collect(Collectors.toMap(
                        SalesOutboundItemRequest::id,
                        Function.identity(),
                        (left, right) -> left
                ));
        List<SalesOutboundItemRequest> restrictedItems = entity.getItems().stream()
                .sorted(Comparator.comparing(SalesOutboundItem::getLineNo, Comparator.nullsLast(Integer::compareTo)))
                .map(item -> restrictImportedOutboundItem(item, requestItemsById.get(item.getId())))
                .toList();
        return new SalesOutboundRequest(
                entity.getOutboundNo(),
                entity.getSalesOrderNo(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getWarehouseId(),
                entity.getWarehouseName(),
                request.outboundDate(),
                entity.getStatus(),
                request.remark(),
                restrictedItems,
                request.audit()
        );
    }

    private SalesOutboundItemRequest restrictImportedOutboundItem(
            SalesOutboundItem item,
            SalesOutboundItemRequest requestItem
    ) {
        java.math.BigDecimal weightTon = requestItem == null || requestItem.weightTon() == null
                ? item.getWeightTon()
                : requestItem.weightTon();
        return new SalesOutboundItemRequest(
                item.getId(),
                null,
                item.getSourceSalesOrderItemId(),
                item.getMaterialId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseId(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                weightTon,
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
