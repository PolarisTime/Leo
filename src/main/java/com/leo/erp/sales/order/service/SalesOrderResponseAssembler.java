package com.leo.erp.sales.order.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.mapper.SalesOrderMapper;
import com.leo.erp.sales.order.web.dto.SalesOrderItemResponse;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SalesOrderResponseAssembler {

    private static final String MODULE_KEY = "sales-order";
    private static final String RECEIVABLE = "RECEIVABLE";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final SalesOrderMapper salesOrderMapper;
    private final DocumentChargeItemService chargeItemService;

    public SalesOrderResponseAssembler(SalesOrderMapper salesOrderMapper) {
        this(salesOrderMapper, null);
    }

    @Autowired
    public SalesOrderResponseAssembler(SalesOrderMapper salesOrderMapper,
                                       DocumentChargeItemService chargeItemService) {
        this.salesOrderMapper = salesOrderMapper;
        this.chargeItemService = chargeItemService;
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
        List<DocumentChargeItemResponse> chargeItems = loadChargeItems(entity);
        BigDecimal totalChargeAmount = totalChargeAmount(chargeItems);
        return new SalesOrderResponse(
                response.id(),
                response.orderNo(),
                response.purchaseInboundNo(),
                response.purchaseOrderNo(),
                response.customerCode(),
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
                response.remark(),
                entity.getItems().stream().filter(itemFilter).map(this::toItemResponse).toList(),
                chargeItems,
                totalChargeAmount,
                receivableAmount(response.totalAmount(), totalChargeAmount)
        );
    }

    private List<DocumentChargeItemResponse> loadChargeItems(SalesOrder entity) {
        if (chargeItemService == null || entity.getId() == null) {
            return List.of();
        }
        List<DocumentChargeItemResponse> chargeItems = chargeItemService.listResponses(MODULE_KEY, entity.getId());
        return chargeItems == null ? List.of() : chargeItems;
    }

    private BigDecimal totalChargeAmount(List<DocumentChargeItemResponse> chargeItems) {
        return chargeItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.billable()))
                .filter(item -> RECEIVABLE.equals(item.chargeDirection()))
                .map(DocumentChargeItemResponse::amount)
                .filter(amount -> amount != null)
                .map(this::scaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal receivableAmount(BigDecimal totalAmount, BigDecimal totalChargeAmount) {
        return scaleAmount(totalAmount == null ? BigDecimal.ZERO : totalAmount).add(totalChargeAmount);
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO_AMOUNT;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private SalesOrderItemResponse toItemResponse(SalesOrderItem item) {
        return new SalesOrderItemResponse(
                item.getId(),
                item.getLineNo(),
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
                item.getWarehouseName(),
                item.getBatchNo(),
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
