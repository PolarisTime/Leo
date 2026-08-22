package com.leo.erp.common.charge.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 单据附加费用行保存请求：随所属单据的 create/update 一并提交，
 * 由各单据 Service 在同一事务内转交 DocumentChargeItemService.sync。
 */
public record DocumentChargeItemRequest(
        Long id,
        @NotBlank(message = "费用名称不能为空")
        @Size(max = 128, message = "费用名称长度不能超过128")
        String chargeName,
        Long materialId,
        @NotNull(message = "费用金额不能为空")
        @DecimalMin(value = "0.00", message = "费用金额不能小于0")
        BigDecimal amount,
        @Size(max = 16, message = "单位长度不能超过16")
        String unit,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
