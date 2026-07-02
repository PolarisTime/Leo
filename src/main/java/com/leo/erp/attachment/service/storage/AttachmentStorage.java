package com.leo.erp.attachment.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface AttachmentStorage {

    String type();

    String store(String objectKey, MultipartFile file) throws IOException;

    default String storeBytes(String objectKey, byte[] content, String contentType) throws IOException {
        throw new UnsupportedOperationException("当前附件存储不支持字节内容写入");
    }

    Resource load(String storagePath) throws IOException;

    void delete(String storagePath) throws IOException;
}
