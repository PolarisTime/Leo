package com.leo.erp.auth.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UserAccountPreferencesPayload(
        @Valid
        @Size(max = 200, message = "页面偏好配置数量不能超过200项")
        Map<String, UserListColumnSettingsPayload> pages
) {
}
