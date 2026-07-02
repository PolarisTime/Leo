package com.leo.erp.attachment.service;

public record AttachmentManifestExportResult(
        String objectKey,
        String storagePath,
        int attachmentCount,
        int bindingCount
) {
}
