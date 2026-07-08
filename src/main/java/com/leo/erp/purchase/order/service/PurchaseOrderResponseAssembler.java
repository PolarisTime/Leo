package com.leo.erp.purchase.order.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemResponse;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class PurchaseOrderResponseAssembler {

    private static final String MODULE_KEY = "purchase-order";
    private static final String PAYABLE = "PAYABLE";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PurchaseOrderMapper mapper;
    private final PurchaseOrderAvailabilityService availabilityService;
    private final DocumentChargeItemService chargeItemService;

    public PurchaseOrderResponseAssembler(PurchaseOrderMapper mapper,
                                          PurchaseOrderAvailabilityService availabilityService) {
        this(mapper, availabilityService, null);
    }

    @Autowired
    public PurchaseOrderResponseAssembler(PurchaseOrderMapper mapper,
                                          PurchaseOrderAvailabilityService availabilityService,
                                          DocumentChargeItemService chargeItemService) {
        this.mapper = mapper;
        this.availabilityService = availabilityService;
        this.chargeItemService = chargeItemService;
    }

    PurchaseOrderResponse toDetailResponse(PurchaseOrder order) {
        Map<Long, Integer> allocatedQuantityMap = availabilityService.loadInboundAllocatedQuantityMap(order);
        Map<Long, Integer> salesAllocatedQuantityMap = availabilityService.loadSalesAllocatedQuantityMap(order);
        Map<Long, BigDecimal> salesRemainingWeightMap = availabilityService.loadSalesRemainingWeightMap(order);
        PurchaseOrderResponse response = mapper.toResponse(order);
        List<DocumentChargeItemResponse> chargeItems = loadChargeItems(order);
        BigDecimal totalChargeAmount = totalChargeAmount(chargeItems);
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
                        .map(item -> toItemResponse(item, allocatedQuantityMap, salesAllocatedQuantityMap, salesRemainingWeightMap))
                        .toList(),
                chargeItems,
                totalChargeAmount,
                payableAmount(response.totalAmount(), totalChargeAmount)
        );
    }

    PurchaseOrderResponse toSummaryResponse(PurchaseOrder order) {
        return mapper.toResponse(order);
    }

    private List<DocumentChargeItemResponse> loadChargeItems(PurchaseOrder order) {
        if (chargeItemService == null || order.getId() == null) {
            return List.of();
        }
        List<DocumentChargeItemResponse> chargeItems = chargeItemService.listResponses(MODULE_KEY, order.getId());
        return chargeItems == null ? List.of() : chargeItems;
    }

    private BigDecimal totalChargeAmount(List<DocumentChargeItemResponse> chargeItems) {
        return chargeItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.billable()))
                .filter(item -> PAYABLE.equals(item.chargeDirection()))
                .map(DocumentChargeItemResponse::amount)
                .filter(amount -> amount != null)
                .map(this::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal payableAmount(BigDecimal totalAmount, BigDecimal totalChargeAmount) {
        return scaleAmount(totalAmount == null ? BigDecimal.ZERO : totalAmount).add(totalChargeAmount);
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO_AMOUNT;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private PurchaseOrderItemResponse toItemResponse(PurchaseOrderItem item,
                                                    Map<Long, Integer> allocatedQuantityMap,
                                                    Map<Long, Integer> salesAllocatedQuantityMap,
                                                    Map<Long, BigDecimal> salesRemainingWeightMap) {
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
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
