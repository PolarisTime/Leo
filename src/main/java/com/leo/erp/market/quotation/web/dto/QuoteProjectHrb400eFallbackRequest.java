package com.leo.erp.market.quotation.web.dto;

import jakarta.validation.constraints.NotNull;

/** 更新项目比价「HRB400E 代取」快捷开关请求。 */
public record QuoteProjectHrb400eFallbackRequest(
        @NotNull(message = "HRB400E 代取开关不能为空")
        Boolean hrb400eFallback
) {
}
