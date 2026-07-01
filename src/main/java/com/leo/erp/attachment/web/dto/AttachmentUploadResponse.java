package com.leo.erp.attachment.web.dto;

import java.time.LocalDateTime;

public record AttachmentUploadResponse(
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
    public AttachmentUploadResponse(
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
        this(id, name, fileName, originalFileName, contentType, fileSize, sourceType, uploader, uploadTime,
                previewSupported, previewType, previewUrl, downloadUrl, null, null);
    }
}
