package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemRequest;

import java.util.List;

public record FreightBillRequest(
        String billNo,
        Long carrierId,
        String carrierCode,
        @jakarta.validation.constraints.NotBlank String carrierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        Long vehicleId,
        String vehiclePlate,
        @NotNull LocalDate billTime,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        String status,
        String remark,
        @Valid @NotEmpty List<FreightBillItemRequest> items,
        @Valid List<DocumentChargeItemRequest> chargeItems,
        boolean audit
) {

    public FreightBillRequest {
        if (chargeItems == null) {
            chargeItems = List.of();
        }
    }
}
