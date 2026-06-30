package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AttachmentMetadataService {

    private final AttachmentFileRepository repository;
    private final AttachmentFilenameResolver filenameResolver;

    public AttachmentMetadataService(AttachmentFileRepository repository,
                                     AttachmentFilenameResolver filenameResolver) {
        this.repository = repository;
        this.filenameResolver = filenameResolver;
    }

    @Transactional
    public AttachmentFile saveUploadedFileMetadata(long attachmentId,
                                                   String storedFileName,
                                                   String originalFileName,
                                                   String contentType,
                                                   long fileSize,
                                                   String sourceType,
                                                   String storagePath) {
        AttachmentFile entity = new AttachmentFile();
        entity.setId(attachmentId);
        entity.setFileName(storedFileName);
        entity.setOriginalFileName(originalFileName);
        entity.setFileExtension(filenameResolver.parseFilenameParts(storedFileName, contentType).extension());
        entity.setContentType(contentType);
        entity.setFileSize(fileSize);
        entity.setStoragePath(storagePath);
        entity.setAccessKey(generateAccessKey());
        entity.setSourceType(sourceType);
        return repository.save(entity);
    }

    private String generateAccessKey() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
