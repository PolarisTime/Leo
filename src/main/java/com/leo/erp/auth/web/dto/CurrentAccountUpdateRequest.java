package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CurrentAccountUpdateRequest(
        @NotBlank @Size(max = 64) String userName,
        // 与 sys_user.mobile 的 varchar(20) 一致；此前写 32 会让 21~32 位输入穿过校验后撞库
        @Size(max = 20)
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String mobile,
        @Size(max = 255) String remark
) {
}
