package com.leo.erp.market.region.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 取价地区写入请求。
 *
 * <p>slug 的字符集在这里用 {@code @Pattern} 前置拦截（400/422 语义错误），数据库 CHECK 约束兜底；
 * 状态取值由服务层按 StatusConstants 白名单校验。</p>
 */
public record QuoteRegionRequest(
        @NotBlank(message = "城市名称不能为空")
        @Size(max = 32, message = "城市名称不能超过32个字符")
        String regionName,

        @NotBlank(message = "子域名不能为空")
        @Size(max = 64, message = "子域名不能超过64个字符")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "子域名只能包含小写字母、数字与连字符")
        String slug,

        @NotNull(message = "排序号不能为空")
        @Min(value = 0, message = "排序号不能为负数")
        Integer sortOrder,

        @NotBlank(message = "状态不能为空")
        String status,

        @Size(max = 255, message = "备注不能超过255个字符")
        String remark
) {
}
