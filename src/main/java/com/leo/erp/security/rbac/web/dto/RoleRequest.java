package com.leo.erp.security.rbac.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64, message = "角色编码长度不能超过64")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*", message = "角色编码只能以字母开头，且仅包含字母、数字、下划线或中划线")
        String code,
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 128, message = "角色名称长度不能超过128")
        String name,
        @Size(max = 255, message = "角色描述长度不能超过255")
        String description,
        String status
) {
}
