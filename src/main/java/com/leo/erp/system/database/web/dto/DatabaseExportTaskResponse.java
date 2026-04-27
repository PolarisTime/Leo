package com.leo.erp.system.database.web.dto;

import java.time.LocalDateTime;

public record DatabaseExportTaskResponse(
        Long id,
        String taskNo,
        String status,
        String fileName,
        Long fileSize,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        LocalDateTime expiresAt,
        String downloadUrl
) {
}
