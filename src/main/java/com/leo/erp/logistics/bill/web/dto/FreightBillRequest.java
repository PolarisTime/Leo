package com.leo.erp.logistics.bill.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FreightBillRequest(
        @NotBlank String billNo,
        String outboundNo,
        @NotBlank String carrierName,
        String vehiclePlate,
        @NotBlank String customerName,
        @NotBlank String projectName,
        @NotNull LocalDate billTime,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        String status,
        String deliveryStatus,
        String remark,
        @Valid @NotEmpty List<FreightBillItemRequest> items
) {
}
