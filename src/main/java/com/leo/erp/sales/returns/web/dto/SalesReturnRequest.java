package com.leo.erp.sales.returns.web.dto;

import jakarta.validation.Valid;
import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record SalesReturnRequest(
        String returnNo,
        String salesOrderNo,
        @SnowflakeId Long customerId,
        String customerName,
        @SnowflakeId Long projectId,
        String projectName,
        @SnowflakeId Long warehouseId,
        String warehouseName,
        @NotNull LocalDate returnDate,
        String status,
        String remark,
        @Valid @NotEmpty List<SalesReturnItemRequest> items,
        boolean audit
) {
}
