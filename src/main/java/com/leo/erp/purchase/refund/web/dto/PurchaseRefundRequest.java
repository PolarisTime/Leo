package com.leo.erp.purchase.refund.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record PurchaseRefundRequest(
        String refundNo,
        @NotNull(message = "来源采购订单不能为空")
        @Positive(message = "来源采购订单不合法")
        Long sourcePurchaseOrderId,
        @NotNull(message = "退款日期不能为空")
        LocalDate refundDate,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark
) {
}
