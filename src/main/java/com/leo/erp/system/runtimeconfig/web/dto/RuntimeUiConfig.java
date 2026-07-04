package com.leo.erp.system.runtimeconfig.web.dto;

public record RuntimeUiConfig(
        int defaultPageSize,
        boolean showSnowflakeId,
        RuntimeWatermarkConfig watermark
) {
}
