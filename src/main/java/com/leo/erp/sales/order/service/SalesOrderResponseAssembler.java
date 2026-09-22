package com.leo.erp.sales.order.service;

import com.leo.erp.common.charge.api.DocumentChargeItemResponse;
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
import java.util.stream.Collectors;

@Service
public class SalesOrderResponseAssembler {

    private static final String CHARGE_MODULE_KEY = "sales-order";

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
        List<Long> itemIds = itemIds(entity);
        return assemble(
                entity,
                derivedQuantityService.itemQuantities(itemIds),
                derivedQuantityService.reservedOutboundQuantities(itemIds),
                documentChargeItemService.list(CHARGE_MODULE_KEY, entity.getId()));
    }

    /**
     * 页级批量装配：一次性聚合本页全部订单明细的派生数量、出库占用与附加费用，
     * 再逐单装配，避免逐单重复聚合查询（N+1），响应字段与单条装配保持一致。
     */
    public List<SalesOrderResponse> toDetailResponses(List<SalesOrder> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = entities.stream()
                .flatMap(entity -> itemIds(entity).stream())
                .distinct()
                .toList();
        Map<Long, SalesOrderDerivedQuantityService.Quantities> quantities =
                derivedQuantityService.itemQuantities(itemIds);
        Map<Long, Integer> reservedOutboundQuantities =
                derivedQuantityService.reservedOutboundQuantities(itemIds);
        Map<Long, List<DocumentChargeItemResponse>> chargeItemsByOrder =
                documentChargeItemService.listByDocumentIds(
                        CHARGE_MODULE_KEY, entities.stream().map(SalesOrder::getId).toList());
        return entities.stream()
                .map(entity -> assemble(
                        entity,
                        quantities,
                        reservedOutboundQuantities,
                        chargeItemsByOrder.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    private SalesOrderResponse assemble(SalesOrder entity,
                                        Map<Long, SalesOrderDerivedQuantityService.Quantities> quantities,
                                        Map<Long, Integer> reservedOutboundQuantities,
                                        List<DocumentChargeItemResponse> chargeItems) {
        SalesOrderResponse response = salesOrderMapper.toResponse(entity);
        int deliveredQuantity = 0;
        int returnedQuantity = 0;
        for (SalesOrderItem item : entity.getItems()) {
            SalesOrderDerivedQuantityService.Quantities quantity =
                    quantities.getOrDefault(item.getId(), SalesOrderDerivedQuantityService.Quantities.ZERO);
            deliveredQuantity += quantity.deliveredQuantity();
            returnedQuantity += quantity.returnedQuantity();
        }
        List<SalesOrderItemResponse> items = entity.getItems().stream()
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
                response.priceRuleId(),
                response.priceRuleName(),
                response.priceFloatMode(),
                response.priceFloatValue(),
                items,
                chargeItems,
                response.referencedByFreightBill(),
                response.referencedBySalesOutbound(),
                deliveredQuantity,
                returnedQuantity,
                deliveredQuantity - returnedQuantity
        );
    }

    private static List<Long> itemIds(SalesOrder entity) {
        return entity.getItems().stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
