package com.leo.erp.system.runtimeconfig.web.dto;

public record RuntimeWatermarkConfig(
        boolean enabled,
        String content,
        int fontSize,
        String color,
        int rotate,
        int density
) {
}
