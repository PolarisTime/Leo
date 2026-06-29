package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.mapper.InvoiceIssueMapper;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemResponse;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import org.springframework.stereotype.Service;

@Service
public class InvoiceIssueResponseAssembler {

    private final InvoiceIssueMapper mapper;

    public InvoiceIssueResponseAssembler(InvoiceIssueMapper mapper) {
        this.mapper = mapper;
    }

    InvoiceIssueResponse toDetailResponse(InvoiceIssue entity) {
        InvoiceIssueResponse response = mapper.toResponse(entity);
        return new InvoiceIssueResponse(
                response.id(),
                response.issueNo(),
                response.invoiceNo(),
                response.customerName(),
                response.projectName(),
                response.settlementCompanyId(),
                response.settlementCompanyName(),
                response.invoiceDate(),
                response.invoiceType(),
                response.amount(),
                response.taxAmount(),
                response.status(),
                response.operatorName(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    InvoiceIssueResponse toSummaryResponse(InvoiceIssue entity) {
        return mapper.toResponse(entity);
    }

    private InvoiceIssueItemResponse toItemResponse(InvoiceIssueItem item) {
        return new InvoiceIssueItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourceSalesOrderItemId(),
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
