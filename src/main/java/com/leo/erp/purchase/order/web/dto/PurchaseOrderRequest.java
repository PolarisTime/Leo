package com.leo.erp.purchase.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderRequest(
        @NotBlank(message = "订单编号不能为空")
        String orderNo,
        @NotBlank(message = "供应商不能为空")
        String supplierName,
        @NotNull(message = "订单日期不能为空")
        LocalDate orderDate,
        String buyerName,
        String status,
        String remark,
        @Valid
        @NotEmpty(message = "订单明细不能为空")
        List<PurchaseOrderItemRequest> items
) {
}
