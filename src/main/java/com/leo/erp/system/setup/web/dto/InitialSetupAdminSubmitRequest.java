package com.leo.erp.system.setup.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitialSetupAdminSubmitRequest(
        @Valid InitialSetupAdminRequest admin,
        @Size(max = 128)
        String totpSecret,
        @Pattern(regexp = "^\\d{6}$", message = "2FA 验证码必须为6位数字")
        String totpCode
) {
}
