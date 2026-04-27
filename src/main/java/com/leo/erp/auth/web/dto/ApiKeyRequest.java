package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApiKeyRequest(
        @NotBlank(message = "密钥名称不能为空")
        @Size(max = 64, message = "密钥名称长度不能超过64")
        String keyName,
        @NotBlank(message = "使用范围不能为空")
        @Size(max = 32, message = "使用范围长度不能超过32")
        String usageScope,
        List<@Size(max = 64, message = "允许访问资源编码长度不能超过64") String> allowedResources,
        List<@Size(max = 32, message = "允许动作编码长度不能超过32") String> allowedActions,
        @Positive(message = "有效天数必须大于0")
        @Max(value = 3650, message = "有效天数不能超过3650")
        Long expireDays
) {
}
