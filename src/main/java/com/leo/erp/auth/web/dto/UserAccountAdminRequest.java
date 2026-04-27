package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserAccountAdminRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_.@-]+", message = "登录账号格式不正确") String loginName,
        @Size(max = 128) String password,
        @NotBlank @Size(max = 64) String userName,
        @Size(max = 32) @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String mobile,
        Long departmentId,
        @NotEmpty List<@NotBlank @Size(max = 128) String> roleNames,
        @Pattern(regexp = "全部数据|全部|本部门|本人", message = "数据范围不合法") String dataScope,
        @Size(max = 500) String permissionSummary,
        @Pattern(regexp = "正常|禁用", message = "状态不合法") String status,
        @Size(max = 255) String remark
) {
}
