package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemResponse;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class SalesOutboundResponseAssembler {

    private static final String MODULE_KEY = "sales-outbound";
    private static final String RECEIVABLE = "RECEIVABLE";
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final SalesOutboundMapper mapper;
    private final SalesOutboundSourceService sourceService;
    private final DocumentChargeItemService chargeItemService;

    public SalesOutboundResponseAssembler(SalesOutboundMapper mapper,
                                          SalesOutboundSourceService sourceService) {
        this(mapper, sourceService, null);
    }

    @Autowired
    public SalesOutboundResponseAssembler(SalesOutboundMapper mapper,
                                          SalesOutboundSourceService sourceService,
                                          DocumentChargeItemService chargeItemService) {
        this.mapper = mapper;
        this.sourceService = sourceService;
        this.chargeItemService = chargeItemService;
    }

    SalesOutboundResponse toDetailResponse(SalesOutbound entity) {
        SalesOutboundResponse response = mapper.toResponse(entity);
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap =
                sourceService.loadSourceSalesOrderItemMap(entity.getItems());
        List<DocumentChargeItemResponse> chargeItems = loadChargeItems(entity);
        BigDecimal totalChargeAmount = totalChargeAmount(chargeItems);
        return new SalesOutboundResponse(
                response.id(),
                response.outboundNo(),
                response.salesOrderNo(),
                response.customerName(),
                response.projectName(),
                response.warehouseName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.outboundDate(),
                response.totalWeight(),
                response.totalAmount(),
                response.status(),
                response.remark(),
                entity.getItems().stream()
                        .map(item -> toItemResponse(item, sourceSalesOrderItemMap))
                        .toList(),
                chargeItems,
                totalChargeAmount,
                receivableAmount(response.totalAmount(), totalChargeAmount)
        );
    }

    SalesOutboundResponse toSummaryResponse(SalesOutbound entity) {
        return mapper.toResponse(entity);
    }

    private List<DocumentChargeItemResponse> loadChargeItems(SalesOutbound entity) {
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

    private SalesOutboundItemResponse toItemResponse(SalesOutboundItem item,
                                                    Map<Long, SalesOrderItem> sourceSalesOrderItemMap) {
        return new SalesOutboundItemResponse(
                item.getId(),
                item.getLineNo(),
                sourceService.resolveItemSourceNo(item, sourceSalesOrderItemMap),
                item.getSourceSalesOrderItemId(),
                item.getSettlementCompanyId(),
                item.getSettlementCompanyName(),
                item.getMaterialCode(),
                item.getBrand(),
                item.getCategory(),
                item.getMaterial(),
                item.getSpec(),
                item.getLength(),
                item.getUnit(),
                item.getWarehouseName(),
                item.getBatchNo(),
                item.getQuantity(),
                item.getQuantityUnit(),
                item.getPieceWeightTon(),
                item.getPiecesPerBundle(),
                item.getWeightTon(),
                item.getUnitPrice(),
                item.getAmount()
        );
    }
}
