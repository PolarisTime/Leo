package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceiptItem;
import com.leo.erp.finance.invoicereceipt.mapper.InvoiceReceiptMapper;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemResponse;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import org.springframework.stereotype.Service;

@Service
public class InvoiceReceiptResponseAssembler {

    private final InvoiceReceiptMapper mapper;

    public InvoiceReceiptResponseAssembler(InvoiceReceiptMapper mapper) {
        this.mapper = mapper;
    }

    InvoiceReceiptResponse toDetailResponse(InvoiceReceipt entity) {
        InvoiceReceiptResponse response = mapper.toResponse(entity);
        return new InvoiceReceiptResponse(
                response.id(),
                response.receiveNo(),
                response.invoiceNo(),
                response.supplierName(),
                response.invoiceTitle(),
                response.invoiceDate(),
                response.invoiceType(),
                response.amount(),
                response.taxAmount(),
                response.status(),
                response.deletedFlag(),
                response.operatorName(),
                response.remark(),
                entity.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    InvoiceReceiptResponse toSummaryResponse(InvoiceReceipt entity) {
        return mapper.toResponse(entity);
    }

    private InvoiceReceiptItemResponse toItemResponse(InvoiceReceiptItem item) {
        return new InvoiceReceiptItemResponse(
                item.getId(),
                item.getLineNo(),
                item.getSourceNo(),
                item.getSourcePurchaseOrderItemId(),
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
