package com.leo.erp.auth.web.dto;

import com.leo.erp.auth.domain.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理员编辑用户资料；密码通过独立的 password-resets 资源重置。 */
public record UserAccountUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名长度不能超过64")
        String userName,
        @Size(max = 20, message = "手机号长度不能超过20")
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String mobile,
        UserStatus status
) {
}
