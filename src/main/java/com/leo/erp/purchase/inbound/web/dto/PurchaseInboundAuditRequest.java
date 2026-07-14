package com.leo.erp.purchase.inbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PurchaseInboundAuditRequest(
        @NotNull Boolean closePurchaseOrder,
        @Valid @NotNull List<OverToleranceConfirmation> overToleranceConfirmations
) {
    public record OverToleranceConfirmation(
            @NotNull Long inboundItemId,
            @NotBlank String reasonCode,
            @Size(max = 255) String remark
    ) {
    }
}
