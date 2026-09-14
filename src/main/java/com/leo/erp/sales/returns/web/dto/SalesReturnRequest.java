package com.leo.erp.sales.returns.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesReturnRequest(
        String returnNo,
        String salesOrderNo,
        Long customerId,
        String customerName,
        Long projectId,
        String projectName,
        Long warehouseId,
        String warehouseName,
        @NotNull LocalDate returnDate,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesReturnItemRequest> items,
        boolean audit
) {
}
