package com.leo.erp.system.runtimeconfig.web.dto;

public record RuntimeConfigResponse(
        RuntimeUiConfig ui,
        RuntimeBusinessConfig business,
        RuntimeFeatureConfig features
) {
}
