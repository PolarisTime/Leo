package com.leo.erp.finance.payment.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentRequest(
        String paymentNo,
        @jakarta.validation.constraints.NotBlank(message = "业务类型不能为空")
        String businessType,
        String paymentPurpose,
        String counterpartyCode,
        @jakarta.validation.constraints.NotBlank(message = "往来单位不能为空")
        String counterpartyName,
        Long sourceStatementId,
        Long sourcePurchaseOrderId,
        String purchaseOrderNo,
        String supplierCode,
        String supplierName,
        Long settlementCompanyId,
        String settlementCompanyName,
        @NotNull(message = "付款日期不能为空")
        LocalDate paymentDate,
        @jakarta.validation.constraints.NotBlank(message = "付款方式不能为空")
        String payType,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.00", message = "金额不能小于0")
        BigDecimal amount,
        @jakarta.validation.constraints.NotBlank(message = "状态不能为空")
        String status,
        @jakarta.validation.constraints.NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark,
        @Valid
        List<PaymentAllocationRequest> items
) {
    public PaymentRequest(String paymentNo,
                          String businessType,
                          String counterpartyCode,
                          String counterpartyName,
                          Long sourceStatementId,
                          LocalDate paymentDate,
                          String payType,
                          BigDecimal amount,
                          String status,
                          String operatorName,
                          String remark,
                          List<PaymentAllocationRequest> items) {
        this(
                paymentNo,
                businessType,
                null,
                counterpartyCode,
                counterpartyName,
                sourceStatementId,
                null,
                null,
                null,
                null,
                null,
                null,
                paymentDate,
                payType,
                amount,
                status,
                operatorName,
                remark,
                items
        );
    }

    public PaymentRequest(String paymentNo,
                          String businessType,
                          String counterpartyName,
                          Long sourceStatementId,
                          LocalDate paymentDate,
                          String payType,
                          BigDecimal amount,
                          String status,
                          String operatorName,
                          String remark,
                          List<PaymentAllocationRequest> items) {
        this(
                paymentNo,
                businessType,
                null,
                null,
                counterpartyName,
                sourceStatementId,
                null,
                null,
                null,
                null,
                null,
                null,
                paymentDate,
                payType,
                amount,
                status,
                operatorName,
                remark,
                items
        );
    }
}
