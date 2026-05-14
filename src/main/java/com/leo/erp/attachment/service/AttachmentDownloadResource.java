package com.leo.erp.attachment.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record AttachmentDownloadResource(
        Resource resource,
        MediaType contentType,
        String contentDisposition
) {
}
