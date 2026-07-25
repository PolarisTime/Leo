package com.leo.erp.statement.freight.web.dto;

import java.time.LocalDateTime;

public record FreightStatementAttachmentResponse(
        Long id,
        String name,
        String fileName,
        String originalFileName,
        String contentType,
        Long fileSize,
        String sourceType,
        String uploader,
        LocalDateTime uploadTime,
        Boolean previewSupported,
        String previewType,
        String previewUrl,
        String downloadUrl,
        String storageType,
        String storageLabel
) {
}
