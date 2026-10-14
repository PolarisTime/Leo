package com.leo.erp.market.lengthpremium.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 定尺加价写入请求。
 *
 * <p>加价允许为负（优惠），故只做合理区间限制而非非负校验；状态取值由服务层按
 * StatusConstants 白名单校验，给出 422 语义错误。</p>
 */
public record LengthPremiumRequest(
        @NotBlank(message = "定尺名称不能为空")
        @Size(max = 32, message = "定尺名称不能超过32个字符")
        String lengthName,

        @NotNull(message = "加价不能为空")
        @Min(value = -100000, message = "加价不能小于 -100000")
        @Max(value = 100000, message = "加价不能大于 100000")
        Integer premium,

        @NotNull(message = "排序号不能为空")
        @Min(value = 0, message = "排序号不能为负数")
        Integer sortOrder,

        @NotBlank(message = "状态不能为空")
        String status,

        @Size(max = 255, message = "备注不能超过255个字符")
        String remark
) {
}
