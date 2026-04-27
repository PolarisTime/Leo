package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
        @NotBlank(message = "当前密码不能为空") @Size(max = 128) String currentPassword,
        @NotBlank(message = "新密码不能为空") @Size(max = 128) String newPassword
) {
}
