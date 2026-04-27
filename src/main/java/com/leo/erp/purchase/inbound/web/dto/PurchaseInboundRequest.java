package com.leo.erp.purchase.inbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record PurchaseInboundRequest(
        @NotBlank String inboundNo,
        String purchaseOrderNo,
        @NotBlank String supplierName,
        @NotBlank String warehouseName,
        @NotNull LocalDate inboundDate,
        @NotBlank String settlementMode,
        String status,
        String remark,
        @Valid @NotEmpty List<PurchaseInboundItemRequest> items
) {
}
