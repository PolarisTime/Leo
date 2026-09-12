package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.service.AttachmentDirectUploadTokenService.DirectUploadTokenPayload;
import com.leo.erp.attachment.service.storage.DirectUploadAttachmentStorage;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class AttachmentUploadService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentUploadService.class);
    private static final String SOURCE_PAGE_UPLOAD = "PAGE_UPLOAD";
    private static final String SOURCE_CLIPBOARD = "CLIPBOARD_PASTE";
    private static final String SHA256_HEX_PATTERN = "^[0-9a-fA-F]{64}$";

    private static final Set<String> BLOCKED_ATTACHMENT_EXTENSIONS = Set.of(
            "jsp", "jspx", "php", "phtml", "asp", "aspx", "exe", "bat", "cmd", "sh", "cgi", "war"
    );

    private final SnowflakeIdGenerator idGenerator;
    private final AttachmentProperties properties;
    private final AttachmentFilenameResolver filenameResolver;
    private final AttachmentStorageResolver storageResolver;
    private final AttachmentMetadataService metadataService;
    private final AttachmentDirectUploadTokenService directUploadTokenService;
    private final AttachmentResponseAssembler responseAssembler;

    public AttachmentUploadService(SnowflakeIdGenerator idGenerator,
                                   AttachmentProperties properties,
                                   AttachmentFilenameResolver filenameResolver,
                                   AttachmentStorageResolver storageResolver,
                                   AttachmentMetadataService metadataService,
                                   AttachmentDirectUploadTokenService directUploadTokenService,
                                   AttachmentResponseAssembler responseAssembler) {
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.filenameResolver = filenameResolver;
        this.storageResolver = storageResolver;
        this.metadataService = metadataService;
        this.directUploadTokenService = directUploadTokenService;
        this.responseAssembler = responseAssembler;
    }

    public AttachmentView upload(
            MultipartFile file, String sourceType, String moduleKey, Long ownerUserId) throws IOException {
        validateUpload(file);
        Long normalizedOwnerUserId = normalizeOwnerUserId(ownerUserId);

        String normalizedSourceType = normalizeSourceType(sourceType);
        String originalFileName = normalizeOriginalFileName(file, normalizedSourceType);
        long attachmentId = idGenerator.nextId();
        String storedFileName = filenameResolver.buildStoredFileName(
                attachmentId,
                originalFileName,
                file.getContentType()
        );

        // Store file outside the DB transaction to avoid holding connections during I/O
        String storagePath = storageResolver.store(buildObjectKey(attachmentId, storedFileName), file);
        AttachmentFile saved;
        try {
            saved = metadataService.saveUploadedFileMetadata(
                    attachmentId,
                    normalizedOwnerUserId,
                    storedFileName,
                    originalFileName,
                    file.getContentType(),
                    file.getSize(),
                    normalizedSourceType,
                    storagePath
            );
        } catch (RuntimeException | Error ex) {
            cleanupStoredFileQuietly(storagePath);
            throw ex;
        }

        return responseAssembler.toResponse(saved, moduleKey);
    }

    public AttachmentService.DirectUploadPrepareResult prepareDirectUpload(
            String fileName,
            String contentType,
            long fileSize,
            String sourceType,
            String moduleKey,
            String sha256Hex,
            Long ownerUserId) {
        validateUploadMetadata(fileName, fileSize);
        String normalizedSha256Hex = normalizeSha256Hex(sha256Hex);
        Long normalizedOwnerUserId = normalizeOwnerUserId(ownerUserId);

        String normalizedSourceType = normalizeSourceType(sourceType);
        String originalFileName = normalizeOriginalFileName(fileName, contentType, normalizedSourceType);
        long attachmentId = idGenerator.nextId();
        String storedFileName = filenameResolver.buildStoredFileName(attachmentId, originalFileName, contentType);
        String objectKey = buildObjectKey(attachmentId, storedFileName);
        DirectUploadAttachmentStorage.PresignedUpload presigned =
                storageResolver.prepareDirectUpload(objectKey, contentType, fileSize, normalizedSha256Hex);
        DirectUploadTokenPayload payload = new DirectUploadTokenPayload(
                attachmentId,
                objectKey,
                presigned.storagePath(),
                storedFileName,
                originalFileName,
                contentType,
                fileSize,
                normalizedSourceType,
                normalizeModuleKey(moduleKey),
                normalizedOwnerUserId,
                normalizedSha256Hex,
                presigned.expiresAt().getEpochSecond()
        );
        return new AttachmentService.DirectUploadPrepareResult(
                attachmentId,
                directUploadTokenService.issue(payload),
                objectKey,
                presigned.storagePath(),
                presigned.uploadUrl(),
                presigned.method(),
                presigned.headers(),
                presigned.expiresAt()
        );
    }

    public AttachmentView completeDirectUpload(
            Long attachmentId,
            String token,
            String moduleKey,
            Long ownerUserId
    ) {
        Long normalizedOwnerUserId = normalizeOwnerUserId(ownerUserId);
        DirectUploadTokenPayload payload = directUploadTokenService.verify(
                token,
                attachmentId,
                moduleKey,
                normalizedOwnerUserId
        );
        storageResolver.verifyDirectUpload(payload.storagePath(), payload.fileSize(), payload.sha256Hex());
        AttachmentFile saved;
        try {
            saved = metadataService.saveUploadedFileMetadata(
                    payload.attachmentId(),
                    payload.ownerUserId(),
                    payload.storedFileName(),
                    payload.originalFileName(),
                    payload.contentType(),
                    payload.fileSize(),
                    payload.sourceType(),
                    payload.storagePath()
            );
        } catch (RuntimeException | Error ex) {
            cleanupStoredFileQuietly(payload.storagePath());
            throw ex;
        }
        return responseAssembler.toResponse(saved, moduleKey);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        validateUploadMetadata(file.getOriginalFilename(), file.getSize());
    }

    private void validateUploadMetadata(String originalFilename, long fileSize) {
        if (fileSize <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        if (fileSize > properties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件超过大小限制");
        }
        if (originalFilename != null && !originalFilename.isBlank() && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (BLOCKED_ATTACHMENT_EXTENSIONS.contains(ext)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的文件类型: ." + ext);
            }
        }
    }

    private String normalizeSha256Hex(String sha256Hex) {
        if (sha256Hex == null || !sha256Hex.matches(SHA256_HEX_PATTERN)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件校验值无效");
        }
        return sha256Hex.trim().toLowerCase(Locale.ROOT);
    }

    private Long normalizeOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件所有者无效");
        }
        return ownerUserId;
    }

    private String buildObjectKey(long attachmentId, String fileName) {
        LocalDate today = LocalDate.now();
        return normalizedKeyPrefix()
                + today.getYear()
                + "/"
                + String.format("%02d", today.getMonthValue())
                + "/"
                + attachmentId
                + "/"
                + fileName;
    }

    private String normalizedKeyPrefix() {
        String keyPrefix = properties.getStorage().getKeyPrefix();
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "";
        }
        String normalized = keyPrefix.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private String normalizeOriginalFileName(MultipartFile file, String sourceType) {
        return normalizeOriginalFileName(file.getOriginalFilename(), file.getContentType(), sourceType);
    }

    private String normalizeOriginalFileName(String originalFileName, String contentType, String sourceType) {
        if (originalFileName == null || originalFileName.isBlank()) {
            String baseName = SOURCE_CLIPBOARD.equals(sourceType) ? "clipboard" : "upload";
            AttachmentFilenameResolver.FilenameParts parts = filenameResolver.parseFilenameParts("", contentType);
            return parts.extension().isBlank() ? baseName : baseName + "." + parts.extension();
        }
        return originalFileName;
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return SOURCE_PAGE_UPLOAD;
        }
        String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_PAGE_UPLOAD.equals(normalized) && !SOURCE_CLIPBOARD.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的上传来源");
        }
        return normalized;
    }

    private String normalizeModuleKey(String moduleKey) {
        return moduleKey == null ? "" : moduleKey.trim();
    }

    private void cleanupStoredFileQuietly(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            storageResolver.delete(storagePath);
        } catch (Exception ex) {
            // Best-effort cleanup only. The original persistence error should be preserved.
            log.warn("Failed to cleanup storage file: {}", storagePath, ex);
        }
    }
}
