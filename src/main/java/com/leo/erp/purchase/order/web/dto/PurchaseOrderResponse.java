package com.leo.erp.purchase.order.web.dto;

import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
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
        List<PurchaseOrderItemResponse> items,
        List<DocumentChargeItemResponse> chargeItems,
        BigDecimal totalChargeAmount,
        BigDecimal payableAmount
) {
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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
                supplierName,
                orderDate,
                buyerName,
                settlementCompanyId,
                settlementCompanyName,
                totalWeight,
                totalAmount,
                status,
                remark,
                items,
                List.of(),
                ZERO_AMOUNT,
                totalAmount
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
                supplierName,
                orderDate,
                buyerName,
                null,
                null,
                totalWeight,
                totalAmount,
                status,
                remark,
                items
        );
    }
}
