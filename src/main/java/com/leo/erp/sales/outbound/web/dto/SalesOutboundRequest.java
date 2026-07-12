package com.leo.erp.sales.outbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesOutboundRequest(
        String outboundNo,
        String salesOrderNo,
        Long customerId,
        @jakarta.validation.constraints.NotBlank String customerName,
        Long projectId,
        @jakarta.validation.constraints.NotBlank String projectName,
        Long warehouseId,
        String warehouseName,
        @NotNull LocalDate outboundDate,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesOutboundItemRequest> items
) {
    public SalesOutboundRequest(String outboundNo,
                                String salesOrderNo,
                                String customerName,
                                String projectName,
                                String warehouseName,
                                LocalDate outboundDate,
                                String status,
                                String remark,
                                List<SalesOutboundItemRequest> items) {
        this(outboundNo, salesOrderNo, null, customerName, null, projectName, null, warehouseName,
                outboundDate, status, remark, items);
    }
}
