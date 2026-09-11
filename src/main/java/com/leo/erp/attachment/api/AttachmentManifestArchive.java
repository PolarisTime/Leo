package com.leo.erp.attachment.api;

/**
 * 附件恢复清单导出结果：既包含已持久化对象的元数据，也携带可直接作为文件资源返回的内容字节。
 */
public record AttachmentManifestArchive(
        String objectKey,
        String storagePath,
        String fileName,
        String contentType,
        byte[] content,
        int attachmentCount,
        int bindingCount
) {
}
