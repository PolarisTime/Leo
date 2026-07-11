package com.leo.erp.finance.ledgeradjustment.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerAdjustmentRequest(
        String adjustmentNo,
        @NotBlank(message = "方向不能为空")
        String direction,
        @NotBlank(message = "往来类型不能为空")
        String counterpartyType,
        @NotBlank(message = "往来单位编码不能为空")
        String counterpartyCode,
        @NotBlank(message = "往来单位不能为空")
        String counterpartyName,
        @NotNull(message = "结算主体不能为空")
        Long settlementCompanyId,
        String settlementCompanyName,
        Long projectId,
        String projectName,
        @NotNull(message = "调整日期不能为空")
        LocalDate adjustmentDate,
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.01", message = "金额必须大于0")
        BigDecimal amount,
        @NotBlank(message = "调整类型不能为空")
        String adjustmentType,
        @NotBlank(message = "余额影响不能为空")
        String effect,
        @NotBlank(message = "状态不能为空")
        String status,
        @NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark
) {
}
