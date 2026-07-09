package com.leo.erp.purchase.order.service;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PurchaseOrderResponseAssembler {

    private final PurchaseOrderMapper mapper;
    private final PurchaseOrderAvailabilityService availabilityService;

    public PurchaseOrderResponseAssembler(PurchaseOrderMapper mapper,
                                          PurchaseOrderAvailabilityService availabilityService) {
        this.mapper = mapper;
        this.availabilityService = availabilityService;
    }

    PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        Map<Long, Integer> allocatedQuantityMap = availabilityService.loadInboundAllocatedQuantityMap(order);
        Map<Long, Integer> salesAllocatedQuantityMap = availabilityService.loadSalesAllocatedQuantityMap(order);
        Map<Long, BigDecimal> salesRemainingWeightMap = availabilityService.loadSalesRemainingWeightMap(order);
        Map<Long, BigDecimal> lockedSalesWeightMap = availabilityService.loadLockedSalesWeightMap(order);
        PurchaseOrderResponse response = mapper.toResponse(order);
        return new PurchaseOrderResponse(
                response.id(),
                response.orderNo(),
                response.supplierName(),
                response.orderDate(),
                response.buyerName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.totalWeight(),
                response.totalAmount(),
                response.status(),
                response.remark(),
                order.getItems().stream()
                        .map(item -> toItemResponse(
                                item,
                                allocatedQuantityMap,
                                salesAllocatedQuantityMap,
                                salesRemainingWeightMap,
                                lockedSalesWeightMap
                        ))
                        .toList()
        );
    }

    PurchaseOrderResponse toSummaryResponse(PurchaseOrder order) {
        return mapper.toResponse(order);
    }

    private PurchaseOrderItemResponse toItemResponse(PurchaseOrderItem item,
                                                    Map<Long, Integer> allocatedQuantityMap,
                                                    Map<Long, Integer> salesAllocatedQuantityMap,
                                                    Map<Long, BigDecimal> salesRemainingWeightMap,
                                                    Map<Long, BigDecimal> lockedSalesWeightMap) {
        return new PurchaseOrderItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getPurchaseOrder() == null ? null : item.getPurchaseOrder().getSettlementCompanyId(),
                item.getPurchaseOrder() == null ? null : item.getPurchaseOrder().getSettlementCompanyName(),
                item.getWarehouseName(),
                item.getBatchNo(),
                availabilityService.remainingQuantity(item, allocatedQuantityMap),
                availabilityService.remainingQuantity(item, salesAllocatedQuantityMap),
                availabilityService.salesRemainingWeightTon(item, salesAllocatedQuantityMap, salesRemainingWeightMap),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getActualWeightTon(),
                item.getActualPieceWeightTon(),
                availabilityService.lockedSalesWeightTon(item, lockedSalesWeightMap),
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
