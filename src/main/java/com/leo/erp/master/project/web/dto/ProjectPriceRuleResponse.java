package com.leo.erp.master.project.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

/** 项目价格规定(网价浮动规则)响应。 */
public record ProjectPriceRuleResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String mode,
        BigDecimal amount,
        String remark,
        Integer sortOrder
) {
}
