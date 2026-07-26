package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.PageResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillSalesOrderCandidateItemResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillSalesOrderCandidateResponse;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FreightBillSalesOrderCandidateResponseAssembler {

    public PageResponse<FreightBillSalesOrderCandidateResponse> toPageResponse(
            PageResponse<SalesOrderSourceSnapshot> page
    ) {
        return new PageResponse<>(
                page.content().stream().map(this::toResponse).toList(),
                page.totalElements(),
                page.totalPages(),
                page.currentPage(),
                page.pageSize(),
                page.hasMore()
        );
    }

    private FreightBillSalesOrderCandidateResponse toResponse(SalesOrderSourceSnapshot snapshot) {
        return new FreightBillSalesOrderCandidateResponse(
                snapshot.id(),
                snapshot.orderNo(),
                snapshot.purchaseInboundNo(),
                snapshot.purchaseOrderNo(),
                snapshot.customerCode(),
                snapshot.customerId(),
                snapshot.customerName(),
                snapshot.projectId(),
                snapshot.projectName(),
                snapshot.settlementCompanyId(),
                snapshot.settlementCompanyName(),
                snapshot.deliveryDate(),
                snapshot.salesName(),
                snapshot.totalWeight(),
                snapshot.totalAmount(),
                snapshot.status(),
                snapshot.deletedFlag(),
                snapshot.remark(),
                toItemResponses(snapshot.items())
        );
    }

    private List<FreightBillSalesOrderCandidateItemResponse> toItemResponses(
            List<SalesOrderSourceItemSnapshot> items
    ) {
        if (items == null) {
            return null;
        }
        return items.stream().map(this::toItemResponse).toList();
    }

    private FreightBillSalesOrderCandidateItemResponse toItemResponse(SalesOrderSourceItemSnapshot item) {
        return new FreightBillSalesOrderCandidateItemResponse(
                item.id(),
                item.lineNo(),
                item.materialId(),
                item.materialCode(),
                item.brand(),
                item.category(),
                item.material(),
                item.spec(),
                item.length(),
                item.unit(),
                item.sourceInboundItemId(),
                item.sourcePurchaseOrderItemId(),
                item.settlementCompanyId(),
                item.settlementCompanyName(),
                item.warehouseId(),
                item.warehouseName(),
                item.batchNo(),
                item.batchNoNormalized(),
                item.quantity(),
                item.quantityUnit(),
                item.pieceWeightTon(),
                item.piecesPerBundle(),
                item.weightTon(),
                item.unitPrice(),
                item.amount(),
                item.originalWeightTon()
        );
    }
}
