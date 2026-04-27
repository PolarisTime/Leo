package com.leo.erp.finance.invoiceissue.web.dto;

import java.math.BigDecimal;

public record InvoiceIssueItemResponse(
        Long id,
        Integer lineNo,
        String sourceNo,
        Long sourceSalesOrderItemId,
        String materialCode,
        String brand,
        String category,
        String material,
        String spec,
        String length,
        String unit,
        String warehouseName,
        String batchNo,
        Integer quantity,
        String quantityUnit,
        BigDecimal pieceWeightTon,
        Integer piecesPerBundle,
        BigDecimal weightTon,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
