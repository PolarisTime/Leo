package com.leo.erp.sales.outbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesOutboundRequest(
        String outboundNo,
        String salesOrderNo,
        @jakarta.validation.constraints.NotBlank String customerName,
        @jakarta.validation.constraints.NotBlank String projectName,
        String warehouseName,
        @NotNull LocalDate outboundDate,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesOutboundItemRequest> items
) {
}
