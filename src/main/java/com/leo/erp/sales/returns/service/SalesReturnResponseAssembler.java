package com.leo.erp.sales.returns.service;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.sales.returns.web.dto.SalesReturnItemResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SalesReturnResponseAssembler {

    private final SalesReturnSourceService sourceService;

    public SalesReturnResponseAssembler(SalesReturnSourceService sourceService) {
        this.sourceService = sourceService;
    }

    public SalesReturnResponse toDetailResponse(SalesReturn entity) {
        Map<Long, SalesOutboundItem> outboundMap = sourceService.loadSourceOutboundItemMap(
                entity.getItems().stream()
                        .map(SalesReturnItem::getSourceSalesOutboundItemId)
                        .toList());
        Map<Long, FreightBill> freightBillMap = sourceService.loadFreightBillMap(
                entity.getItems().stream()
                        .map(SalesReturnItem::getSourceFreightBillId)
                        .toList());
        return new SalesReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getSalesOrderNo(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getWarehouseId(),
                entity.getWarehouseName(),
                entity.getSettlementCompanyId(),
                entity.getSettlementCompanyName(),
                entity.getReturnDate(),
                entity.getTotalWeight(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.isDeletedFlag(),
                entity.getRemark(),
                entity.getItems().stream()
                        .map(item -> toItemResponse(item, outboundMap, freightBillMap))
                        .toList()
        );
    }

    public SalesReturnResponse toSummaryResponse(SalesReturn entity) {
        return new SalesReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getSalesOrderNo(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getProjectId(),
                entity.getProjectName(),
                entity.getWarehouseId(),
                entity.getWarehouseName(),
                entity.getSettlementCompanyId(),
                entity.getSettlementCompanyName(),
                entity.getReturnDate(),
                entity.getTotalWeight(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.isDeletedFlag(),
                entity.getRemark(),
                null
        );
    }

    private SalesReturnItemResponse toItemResponse(SalesReturnItem item,
                                                   Map<Long, SalesOutboundItem> outboundMap,
                                                   Map<Long, FreightBill> freightBillMap) {
        SalesOutboundItem sourceOutbound = item.getSourceSalesOutboundItemId() == null
                ? null
                : outboundMap.get(item.getSourceSalesOutboundItemId());
        SalesOutbound sourceOutboundHeader = sourceOutbound == null ? null : sourceOutbound.getSalesOutbound();
        FreightBill sourceFreightBill = item.getSourceFreightBillId() == null
                ? null
                : freightBillMap.get(item.getSourceFreightBillId());
        return new SalesReturnItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceSalesOutboundItemId(),
                sourceOutboundHeader == null ? null : sourceOutboundHeader.getOutboundNo(),
                item.getSourceSalesOrderItemId(),
                sourceOutboundHeader == null ? null : sourceOutboundHeader.getSalesOrderNo(),
                item.getSourceFreightBillId(),
                sourceFreightBill == null ? null : sourceFreightBill.getBillNo(),
                item.getSettlementCompanyId(),
                item.getSettlementCompanyName(),
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
                item.getBatchNoNormalized(),
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
