package com.leo.erp.auth.web.dto;

import com.leo.erp.auth.domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull(message = "账号状态不能为空")
        UserStatus status
) {
}
