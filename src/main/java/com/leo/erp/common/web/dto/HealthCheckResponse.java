package com.leo.erp.common.web.dto;

public record HealthCheckResponse(
        String status,
        long freeGb,
        long totalGb
) {

    public static HealthCheckResponse up() {
        return new HealthCheckResponse("UP", 0L, 0L);
    }

    public static HealthCheckResponse down() {
        return new HealthCheckResponse("DOWN", 0L, 0L);
    }

    public static HealthCheckResponse disk(String status, long freeGb, long totalGb) {
        return new HealthCheckResponse(status, freeGb, totalGb);
    }

    public boolean isUp() {
        return "UP".equals(status);
    }
}
