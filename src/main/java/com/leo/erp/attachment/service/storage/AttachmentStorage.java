package com.leo.erp.attachment.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface AttachmentStorage {

    String type();

    String store(String objectKey, MultipartFile file) throws IOException;

    Resource load(String storagePath) throws IOException;

    void delete(String storagePath) throws IOException;
}
