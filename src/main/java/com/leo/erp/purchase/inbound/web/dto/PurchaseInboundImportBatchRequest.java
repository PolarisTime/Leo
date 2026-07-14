package com.leo.erp.purchase.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PurchaseInboundImportBatchRequest(
        @NotNull LocalDate inboundDate,
        @Size(max = 255) String remark
) {
}
