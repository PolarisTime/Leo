package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record Login2faRequest(
        @NotBlank(message = "临时令牌不能为空")
        String tempToken,
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "\\d{6}", message = "验证码必须为6位数字")
        String totpCode
) {
}
