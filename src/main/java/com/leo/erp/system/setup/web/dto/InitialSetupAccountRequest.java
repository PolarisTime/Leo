package com.leo.erp.system.setup.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitialSetupAccountRequest(
        @Size(max = 64)
        @Pattern(regexp = "^$|[A-Za-z0-9_.@-]+$", message = "登录账号格式不正确")
        String loginName,
        @Size(max = 128)
        String password,
        @Size(max = 64)
        String userName,
        @Size(max = 32)
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String mobile
) {
}
