package com.leo.erp.auth.web.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UserListColumnSettingsPayload(
        @Size(max = 200, message = "列顺序配置数量不能超过200项")
        List<String> orderedKeys,
        @Size(max = 200, message = "隐藏列配置数量不能超过200项")
        List<String> hiddenKeys,
        @Size(max = 200, message = "列宽配置数量不能超过200项")
        Map<String, Integer> columnSizes
) {
}
