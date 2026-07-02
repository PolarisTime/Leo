package com.leo.erp.system.oss.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OssCorsConfigureRequest(
        @Valid OssSettingRequest setting,
        @NotBlank(message = "前端访问源不能为空")
        @Size(max = 255, message = "前端访问源不能超过255个字符")
        String origin,
        List<@Size(max = 32, message = "请求方法不能超过32个字符") String> methods
) {
}
