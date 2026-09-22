package com.leo.erp.master.project.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 项目价格规定(网价浮动规则)请求。 */
public record ProjectPriceRuleRequest(
        /** 已有规则 id(编辑时携带, 新增为空)。 */
        Long id,
        @NotBlank(message = "规定名称不能为空") @Size(max = 64, message = "规定名称过长") String name,
        @NotBlank(message = "请选择加价或减价") String mode,
        @NotNull(message = "请填写金额") @DecimalMin(value = "0", message = "金额不能为负") BigDecimal amount,
        @Size(max = 255, message = "备注过长") String remark
) {
}
