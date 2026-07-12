package com.leo.erp.purchase.refund.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseRefundResponse(
        Long id,
        String refundNo,
        Long sourcePurchaseOrderId,
        String purchaseOrderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        LocalDate refundDate,
        Integer totalQuantity,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String operatorName,
        String remark,
        List<PurchaseRefundItemResponse> items
) {
    public PurchaseRefundResponse(Long id,
                                  String refundNo,
                                  Long sourcePurchaseOrderId,
                                  String purchaseOrderNo,
                                  String supplierCode,
                                  String supplierName,
                                  Long settlementCompanyId,
                                  String settlementCompanyName,
                                  LocalDate refundDate,
                                  Integer totalQuantity,
                                  BigDecimal totalWeight,
                                  BigDecimal totalAmount,
                                  String status,
                                  boolean deletedFlag,
                                  String operatorName,
                                  String remark,
                                  List<PurchaseRefundItemResponse> items) {
        this(id, refundNo, sourcePurchaseOrderId, purchaseOrderNo, null, supplierCode, supplierName,
                settlementCompanyId, settlementCompanyName, refundDate, totalQuantity, totalWeight,
                totalAmount, status, deletedFlag, operatorName, remark, items);
    }
}
