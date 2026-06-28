package com.leo.erp.common.web.dto;

public record HealthResponse(
        String status,
        String app,
        String version,
        String traceId,
        String timestamp,
        HealthCheckResponse db,
        HealthCheckResponse redis,
        HealthCheckResponse disk
) {
}
