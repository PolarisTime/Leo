package com.leo.erp.common.web.dto;

import java.time.Instant;

/**
 * 健康检查响应。
 *
 * <p>时间戳使用 ISO-8601（{@link Instant}），与 {@code GET /v2.0/version} 的 {@code buildTime}
 * 保持同一格式；不要退回 {@code yyyy-MM-dd HH:mm:ss} 这类展示口径。</p>
 */
public record HealthResponse(
        String status,
        Instant timestamp
) {
}
