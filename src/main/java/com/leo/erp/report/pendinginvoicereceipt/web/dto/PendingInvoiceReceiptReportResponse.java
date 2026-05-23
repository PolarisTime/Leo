package com.leo.erp.report.pendinginvoicereceipt.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PendingInvoiceReceiptReportResponse(
        Long id,
        String orderNo,
        String supplierName,
        String invoiceTitle,
        LocalDateTime orderDate,
        String materialCode,
        String brand,
        String material,
        String category,
        String spec,
        String length,
        Integer orderQuantity,
        String quantityUnit,
        BigDecimal orderWeightTon,
        BigDecimal receivedInvoiceWeightTon,
        BigDecimal pendingInvoiceWeightTon,
        BigDecimal unitPrice,
        BigDecimal orderAmount,
        BigDecimal receivedInvoiceAmount,
        BigDecimal pendingInvoiceAmount,
        String status
) {
}
