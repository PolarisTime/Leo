package com.leo.erp.attachment.service;

import java.time.LocalDateTime;

public record AttachmentView(
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
        String downloadUrl
) {
}
