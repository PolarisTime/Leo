package com.leo.erp.auth.web.dto;

import com.leo.erp.auth.domain.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserAccountCreateRequest(
        @NotBlank(message = "登录账号不能为空")
        @Size(max = 64, message = "登录账号长度不能超过64")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.@-]*$", message = "登录账号只能包含字母、数字、下划线、点、@、中划线")
        String loginName,
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名长度不能超过64")
        String userName,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度必须在8到128之间")
        String password,
        @Size(max = 20, message = "手机号长度不能超过20")
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String mobile,
        UserStatus status
) {
}
