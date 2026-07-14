package com.leo.erp.sales.order.service;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.sales.order.web.dto.SalesOrderItemResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.springframework.stereotype.Service;

@Service
public class SalesOrderResponseAssembler {

    private final SalesOrderMapper salesOrderMapper;

    public SalesOrderResponseAssembler(SalesOrderMapper salesOrderMapper) {
        this.salesOrderMapper = salesOrderMapper;
    }

    SalesOrderResponse toSummaryResponse(SalesOrder entity) {
        return salesOrderMapper.toResponse(entity);
    }

    SalesOrderResponse toDetailResponse(SalesOrder entity) {
        return toDetailResponse(entity, item -> true);
    }

    SalesOrderResponse toDetailResponse(SalesOrder entity,
                                        java.util.function.Predicate<SalesOrderItem> itemFilter) {
        SalesOrderResponse response = salesOrderMapper.toResponse(entity);
        return new SalesOrderResponse(
                response.id(),
                response.orderNo(),
                response.purchaseInboundNo(),
                response.purchaseOrderNo(),
                response.customerCode(),
                response.customerId(),
                response.customerName(),
                response.projectId(),
                response.projectName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.deliveryDate(),
                response.salesName(),
                response.totalWeight(),
                response.totalAmount(),
                response.status(),
                response.deletedFlag(),
                response.remark(),
                entity.getItems().stream().filter(itemFilter).map(this::toItemResponse).toList(),
                entity.getSalesMode()
        );
    }

    private SalesOrderItemResponse toItemResponse(SalesOrderItem item) {
        return new SalesOrderItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getMaterialId(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getSourceInboundItemId(),
                item.getSourcePurchaseOrderItemId(),
                item.getSettlementCompanyId(),
                item.getSettlementCompanyName(),
                item.getWarehouseId(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getBatchNoNormalized(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount(),
                item.getOriginalWeightTon()
        );
    }
}
