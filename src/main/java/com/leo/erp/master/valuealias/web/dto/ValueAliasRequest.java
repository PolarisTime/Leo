package com.leo.erp.master.valuealias.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 值映射写入请求。
 *
 * <p>维度取值不在这里用注解限定，而由服务层按白名单校验，以便对非法维度返回 422
 * 语义错误而不是 400 格式错误；自映射与同维度源写法重复同样在服务层判定。</p>
 */
public record ValueAliasRequest(
        @NotBlank(message = "维度不能为空")
        String dimension,

        @NotBlank(message = "源写法不能为空")
        @Size(max = 64, message = "源写法不能超过64个字符")
        String sourceValue,

        @NotBlank(message = "目标写法不能为空")
        @Size(max = 64, message = "目标写法不能超过64个字符")
        String targetValue,

        @Size(max = 255, message = "备注不能超过255个字符")
        String remark
) {
}
