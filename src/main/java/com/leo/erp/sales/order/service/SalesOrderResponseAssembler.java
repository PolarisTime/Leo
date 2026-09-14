package com.leo.erp.sales.order.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.sales.order.web.dto.SalesOrderItemResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SalesOrderResponseAssembler {

    private final SalesOrderMapper salesOrderMapper;
    private final DocumentChargeItemService documentChargeItemService;
    private final SalesOrderDerivedQuantityService derivedQuantityService;

    public SalesOrderResponseAssembler(SalesOrderMapper salesOrderMapper,
                                       DocumentChargeItemService documentChargeItemService,
                                       SalesOrderDerivedQuantityService derivedQuantityService) {
        this.salesOrderMapper = salesOrderMapper;
        this.documentChargeItemService = documentChargeItemService;
        this.derivedQuantityService = derivedQuantityService;
    }

    SalesOrderResponse toSummaryResponse(SalesOrder entity) {
        return salesOrderMapper.toResponse(entity);
    }

    public SalesOrderResponse toDetailResponse(SalesOrder entity) {
        return toDetailResponse(entity, item -> true);
    }

    SalesOrderResponse toDetailResponse(SalesOrder entity,
                                        java.util.function.Predicate<SalesOrderItem> itemFilter) {
        SalesOrderResponse response = salesOrderMapper.toResponse(entity);
        List<Long> itemIds = entity.getItems().stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SalesOrderDerivedQuantityService.Quantities> quantities =
                derivedQuantityService.itemQuantities(itemIds);
        Map<Long, Integer> reservedOutboundQuantities =
                derivedQuantityService.reservedOutboundQuantities(itemIds);
        int deliveredQuantity = 0;
        int returnedQuantity = 0;
        for (SalesOrderItem item : entity.getItems()) {
            SalesOrderDerivedQuantityService.Quantities quantity =
                    quantities.getOrDefault(item.getId(), SalesOrderDerivedQuantityService.Quantities.ZERO);
            deliveredQuantity += quantity.deliveredQuantity();
            returnedQuantity += quantity.returnedQuantity();
        }
        List<SalesOrderItemResponse> items = entity.getItems().stream()
                .filter(itemFilter)
                .map(item -> toItemResponse(item,
                        quantities.getOrDefault(item.getId(), SalesOrderDerivedQuantityService.Quantities.ZERO),
                        reservedOutboundQuantities.getOrDefault(item.getId(), 0)))
                .toList();
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
                items,
                documentChargeItemService.list("sales-order", entity.getId()),
                response.referencedByFreightBill(),
                response.referencedBySalesOutbound(),
                deliveredQuantity,
                returnedQuantity,
                deliveredQuantity - returnedQuantity
        );
    }

    private SalesOrderItemResponse toItemResponse(SalesOrderItem item,
                                                  SalesOrderDerivedQuantityService.Quantities quantity,
                                                  int reservedOutboundQuantity) {
        int orderQuantity = item.getQuantity() == null ? 0 : item.getQuantity();
        int outboundRemainingQuantity = Math.max(orderQuantity - reservedOutboundQuantity, 0);
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
                item.getOriginalWeightTon(),
                quantity.deliveredQuantity(),
                quantity.returnedQuantity(),
                quantity.deliveredNetQuantity(),
                outboundRemainingQuantity
        );
    }
}
