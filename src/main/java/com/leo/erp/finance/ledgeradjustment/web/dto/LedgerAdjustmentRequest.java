package com.leo.erp.finance.ledgeradjustment.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
        Long counterpartyId,
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
        @DecimalMin(value = "0.01", message = ValidationMessages.AMOUNT_POSITIVE)
        @Digits(integer = 12, fraction = 2, message = ValidationMessages.AMOUNT_PRECISION)
        BigDecimal amount,
        @NotBlank(message = "调整类型不能为空")
        String adjustmentType,
        @NotBlank(message = "余额影响不能为空")
        String effect,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        String status,
        @NotBlank(message = "经办人不能为空")
        String operatorName,
        String remark
) {
    public LedgerAdjustmentRequest(String adjustmentNo,
                                   String direction,
                                   String counterpartyType,
                                   String counterpartyCode,
                                   String counterpartyName,
                                   Long settlementCompanyId,
                                   String settlementCompanyName,
                                   Long projectId,
                                   String projectName,
                                   LocalDate adjustmentDate,
                                   BigDecimal amount,
                                   String adjustmentType,
                                   String effect,
                                   String status,
                                   String operatorName,
                                   String remark) {
        this(adjustmentNo, direction, counterpartyType, null, counterpartyCode, counterpartyName,
                settlementCompanyId, settlementCompanyName, projectId, projectName, adjustmentDate,
                amount, adjustmentType, effect, status, operatorName, remark);
    }
}
