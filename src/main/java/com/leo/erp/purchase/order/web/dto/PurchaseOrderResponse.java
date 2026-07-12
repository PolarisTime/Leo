package com.leo.erp.purchase.order.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        LocalDateTime orderDate,
        String buyerName,
        Long settlementCompanyId,
        String settlementCompanyName,
        BigDecimal totalWeight,
        BigDecimal totalAmount,
        String status,
        boolean deletedFlag,
        String remark,
        List<PurchaseOrderItemResponse> items
) {
    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierCode,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(id, orderNo, null, supplierCode, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items);
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 boolean deletedFlag,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(id, orderNo, null, null, supplierName, orderDate, buyerName, settlementCompanyId,
                settlementCompanyName, totalWeight, totalAmount, status, deletedFlag, remark, items);
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 Long settlementCompanyId,
                                 String settlementCompanyName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(
                id,
                orderNo,
                null,
                null,
                supplierName,
                orderDate,
                buyerName,
                settlementCompanyId,
                settlementCompanyName,
                totalWeight,
                totalAmount,
                status,
                false,
                remark,
                items
        );
    }

    public PurchaseOrderResponse(Long id,
                                 String orderNo,
                                 String supplierName,
                                 LocalDateTime orderDate,
                                 String buyerName,
                                 BigDecimal totalWeight,
                                 BigDecimal totalAmount,
                                 String status,
                                 String remark,
                                 List<PurchaseOrderItemResponse> items) {
        this(
                id,
                orderNo,
                null,
                null,
                supplierName,
                orderDate,
                buyerName,
                null,
                null,
                totalWeight,
                totalAmount,
                status,
                false,
                remark,
                items
        );
    }
}
