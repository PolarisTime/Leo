package com.leo.erp.master.customer.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 客户下拉选项。
 *
 * <p>不含结算主体：结算主体由项目资料决定，客户选项不再携带默认主体，
 * 前端也不应再用客户选项去回填单据/项目的结算主体。</p>
 */
public record CustomerOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String label,
        String value,
        String customerCode,
        String customerName
) {
}
