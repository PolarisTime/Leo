package com.leo.erp.attachment.service.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public interface DirectUploadAttachmentStorage extends AttachmentStorage {

    PresignedUpload prepareDirectUpload(String objectKey, String contentType, long fileSize, String sha256Hex);

    void verifyDirectUpload(String storagePath, long expectedFileSize, String expectedSha256Hex);

    URI createPresignedAccessUrl(String storagePath, String fileName, String contentType, boolean inline);

    record PresignedUpload(
            URI uploadUrl,
            String method,
            Map<String, String> headers,
            String storagePath,
            Instant expiresAt
    ) {
    }
}
