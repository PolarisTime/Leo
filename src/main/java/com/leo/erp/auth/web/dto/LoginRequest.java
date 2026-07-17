package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "登录账号不能为空")
        String loginName,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
