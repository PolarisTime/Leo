package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
        @NotBlank(message = "当前密码不能为空") @Size(min = 8, max = 128, message = "密码长度必须在 8-128 之间") String currentPassword,
        @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 128, message = "密码长度必须在 8-128 之间") String newPassword
) {
}
