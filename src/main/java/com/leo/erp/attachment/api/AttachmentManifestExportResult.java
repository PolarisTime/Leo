package com.leo.erp.attachment.api;

public record AttachmentManifestExportResult(
        String objectKey,
        String storagePath,
        int attachmentCount,
        int bindingCount
) {
}
