package com.leo.erp.system.setup.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitialSetupTotpSetupRequest(
        @Size(max = 64)
        @Pattern(regexp = "^$|[A-Za-z0-9_.@-]+$", message = "管理员登录账号格式不正确")
        String loginName
) {
}
