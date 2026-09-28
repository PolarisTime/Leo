package com.leo.erp.market.pricelist.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 值映射新增/编辑请求。
 *
 * <p>维度为文本(非枚举), 因此非法维度不会被反序列化拦成 400, 而是由服务层校验后精确返回 422;
 * 空值由 {@code @NotBlank} 拦截并统一按 422 返回。</p>
 */
public record ValueAliasRequest(
        @NotBlank(message = "维度不能为空")
        @Size(max = 16, message = "维度长度不能超过16个字符")
        String dimension,
        @NotBlank(message = "源值不能为空")
        @Size(max = 64, message = "源值长度不能超过64个字符")
        String sourceValue,
        @NotBlank(message = "目标值不能为空")
        @Size(max = 64, message = "目标值长度不能超过64个字符")
        String targetValue,
        @Size(max = 255, message = "备注长度不能超过255个字符")
        String remark
) {
}
