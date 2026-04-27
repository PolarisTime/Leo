package com.leo.erp.sales.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesOrderRequest(
        @NotBlank String orderNo,
        String purchaseInboundNo,
        @NotBlank String customerName,
        @NotBlank String projectName,
        @NotNull LocalDate deliveryDate,
        @NotBlank String salesName,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesOrderItemRequest> items
) {
}
