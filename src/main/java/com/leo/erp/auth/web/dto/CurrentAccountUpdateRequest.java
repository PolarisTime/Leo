package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CurrentAccountUpdateRequest(
        @NotBlank @Size(max = 64) String userName,
        @Size(max = 32)
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String mobile,
        @Size(max = 255) String remark
) {
}
