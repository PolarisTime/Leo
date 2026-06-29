package com.leo.erp.sales.outbound.service;

import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.mapper.SalesOutboundMapper;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemResponse;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SalesOutboundResponseAssembler {

    private final SalesOutboundMapper mapper;
    private final SalesOutboundSourceService sourceService;

    public SalesOutboundResponseAssembler(SalesOutboundMapper mapper,
                                          SalesOutboundSourceService sourceService) {
        this.mapper = mapper;
        this.sourceService = sourceService;
    }

    SalesOutboundResponse toDetailResponse(SalesOutbound entity) {
        SalesOutboundResponse response = mapper.toResponse(entity);
        Map<Long, SalesOrderItem> sourceSalesOrderItemMap =
                sourceService.loadSourceSalesOrderItemMap(entity.getItems());
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
                        .toList()
        );
    }

    SalesOutboundResponse toSummaryResponse(SalesOutbound entity) {
        return mapper.toResponse(entity);
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
