package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员重置指定账号的密码，不校验原密码。 */
public record UserPasswordResetRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度必须在8到128之间")
        String newPassword
) {
}
