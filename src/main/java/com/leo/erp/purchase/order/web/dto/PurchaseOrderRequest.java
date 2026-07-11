package com.leo.erp.purchase.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record PurchaseOrderRequest(
        String orderNo,
        String supplierCode,
        @jakarta.validation.constraints.NotBlank(message = "供应商不能为空")
        String supplierName,
        @NotNull(message = "订单日期不能为空")
        LocalDateTime orderDate,
        String buyerName,
        @NotNull(message = "采购结算主体不能为空")
        Long settlementCompanyId,
        String status,
        String remark,
        @Valid
        @NotEmpty(message = "订单明细不能为空")
        List<PurchaseOrderItemRequest> items
) {
    public PurchaseOrderRequest(String orderNo,
                                String supplierName,
                                LocalDateTime orderDate,
                                String buyerName,
                                Long settlementCompanyId,
                                String status,
                                String remark,
                                List<PurchaseOrderItemRequest> items) {
        this(orderNo, null, supplierName, orderDate, buyerName, settlementCompanyId, status, remark, items);
    }
}
