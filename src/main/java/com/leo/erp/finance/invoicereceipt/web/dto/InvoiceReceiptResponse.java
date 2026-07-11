package com.leo.erp.finance.invoicereceipt.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceReceiptResponse(
        Long id,
        String receiveNo,
        String invoiceNo,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        String invoiceTitle,
        LocalDate invoiceDate,
        String invoiceType,
        BigDecimal amount,
        BigDecimal taxAmount,
        String status,
        boolean deletedFlag,
        String operatorName,
        String remark,
        List<InvoiceReceiptItemResponse> items
) {
    public InvoiceReceiptResponse(Long id,
                                  String receiveNo,
                                  String invoiceNo,
                                  String supplierName,
                                  Long settlementCompanyId,
                                  String settlementCompanyName,
                                  String invoiceTitle,
                                  LocalDate invoiceDate,
                                  String invoiceType,
                                  BigDecimal amount,
                                  BigDecimal taxAmount,
                                  String status,
                                  boolean deletedFlag,
                                  String operatorName,
                                  String remark,
                                  List<InvoiceReceiptItemResponse> items) {
        this(id, receiveNo, invoiceNo, null, supplierName, settlementCompanyId,
                settlementCompanyName, invoiceTitle, invoiceDate, invoiceType, amount,
                taxAmount, status, deletedFlag, operatorName, remark, items);
    }

    public InvoiceReceiptResponse(Long id,
                                  String receiveNo,
                                  String invoiceNo,
                                  String supplierName,
                                  String invoiceTitle,
                                  LocalDate invoiceDate,
                                  String invoiceType,
                                  BigDecimal amount,
                                  BigDecimal taxAmount,
                                  String status,
                                  String operatorName,
                                  String remark,
                                  List<InvoiceReceiptItemResponse> items) {
        this(id, receiveNo, invoiceNo, null, supplierName, null, null, invoiceTitle, invoiceDate, invoiceType,
                amount, taxAmount, status, false, operatorName, remark, items);
    }
}
