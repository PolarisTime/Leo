package com.leo.erp.system.role.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoleSettingRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "角色编码格式不正确") String roleCode,
        @NotBlank @Size(max = 128) String roleName,
        @NotBlank @Size(max = 32) String roleType,
        @NotBlank @Size(max = 32) String dataScope,
        List<@NotBlank @Size(max = 128) String> permissionCodes,
        @Size(max = 500) String permissionSummary,
        @Min(0) Integer userCount,
        @Pattern(regexp = "正常|禁用", message = "状态不合法") String status,
        @Size(max = 255) String remark
) {
}
