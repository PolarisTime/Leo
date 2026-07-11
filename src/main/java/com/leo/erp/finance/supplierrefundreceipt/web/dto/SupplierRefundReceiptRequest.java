package com.leo.erp.finance.supplierrefundreceipt.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierRefundReceiptRequest(
        String refundReceiptNo,
        @NotNull(message = "采购退款单不能为空")
        Long purchaseRefundId,
        @NotNull(message = "到账日期不能为空")
        LocalDate receiptDate,
        @NotBlank(message = "到账方式不能为空")
        String receiptMethod,
        @NotNull(message = "到账金额不能为空")
        @DecimalMin(value = "0.01", message = "到账金额必须大于0")
        @Digits(integer = 12, fraction = 2, message = "到账金额最多保留2位小数")
        BigDecimal amount,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark
) {
}
