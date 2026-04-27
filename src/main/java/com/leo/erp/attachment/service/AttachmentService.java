package com.leo.erp.attachment.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttachmentService {

    private static final String SOURCE_PAGE_UPLOAD = "PAGE_UPLOAD";
    private static final String SOURCE_CLIPBOARD = "CLIPBOARD_PASTE";

    private final AttachmentFileRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final AttachmentProperties properties;
    private final AttachmentFilenameResolver filenameResolver;
    private final UploadRuleService uploadRuleService;
    private final AttachmentStorageResolver storageResolver;

    public AttachmentService(AttachmentFileRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             AttachmentProperties properties,
                             AttachmentFilenameResolver filenameResolver,
                             UploadRuleService uploadRuleService,
                             AttachmentStorageResolver storageResolver) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.filenameResolver = filenameResolver;
        this.uploadRuleService = uploadRuleService;
        this.storageResolver = storageResolver;
    }

    @Transactional
    public AttachmentView upload(MultipartFile file, String sourceType) throws IOException {
        return upload(file, sourceType, null);
    }

    @Transactional
    public AttachmentView upload(MultipartFile file, String sourceType, String moduleKey) throws IOException {
        validateUpload(file);

        String normalizedSourceType = normalizeSourceType(sourceType);
        String originalFileName = normalizeOriginalFileName(file, normalizedSourceType);
        String candidateFileName = uploadRuleService.buildPageUploadFileName(moduleKey, originalFileName, file.getContentType());
        long attachmentId = idGenerator.nextId();
        String storedFileName = extractFileName(candidateFileName);
        String storagePath = storageResolver.store(buildObjectKey(attachmentId, storedFileName), file);

        AttachmentFile entity = new AttachmentFile();
        entity.setId(attachmentId);
        entity.setFileName(storedFileName);
        entity.setOriginalFileName(originalFileName);
        entity.setFileExtension(filenameResolver.parseFilenameParts(storedFileName, file.getContentType()).extension());
        entity.setContentType(file.getContentType());
        entity.setFileSize(file.getSize());
        entity.setStoragePath(storagePath);
        entity.setAccessKey(generateAccessKey());
        entity.setSourceType(normalizedSourceType);
        AttachmentFile saved;
        try {
            saved = repository.save(entity);
        } catch (RuntimeException ex) {
            cleanupStoredFileQuietly(storagePath);
            throw ex;
        }

        AttachmentPresentation presentation = toPresentation(saved, moduleKey);
        return new AttachmentView(
                presentation.id(),
                presentation.name(),
                presentation.fileName(),
                presentation.originalFileName(),
                presentation.contentType(),
                presentation.fileSize(),
                presentation.sourceType(),
                presentation.uploader(),
                presentation.uploadTime(),
                presentation.previewSupported(),
                presentation.previewType(),
                presentation.previewUrl(),
                presentation.downloadUrl()
        );
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> getAttachments(List<Long> ids) {
        return getAttachments(ids, null);
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> getAttachments(List<Long> ids, String moduleKey) {
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AttachmentView> attachmentMap = getAttachmentMap(normalizedIds, moduleKey);
        List<AttachmentView> results = new ArrayList<>(normalizedIds.size());
        for (Long id : normalizedIds) {
            AttachmentView response = attachmentMap.get(id);
            if (response != null) {
                results.add(response);
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids) {
        return getAttachmentMap(ids, null);
    }

    @Transactional(readOnly = true)
    public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids, String moduleKey) {
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllByIdInAndDeletedFlagFalse(normalizedIds).stream()
                .collect(Collectors.toMap(
                        AttachmentFile::getId,
                        entity -> toResponse(entity, moduleKey),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public void validateAttachmentIds(List<Long> ids) {
        List<Long> normalizedIds = normalizeIds(ids);
        if (normalizedIds.isEmpty()) {
            return;
        }
        List<AttachmentFile> entities = repository.findAllByIdInAndDeletedFlagFalse(normalizedIds);
        if (entities.size() != normalizedIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件不存在或已删除");
        }
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadPayload loadForDownload(Long id, String accessKey) {
        AttachmentFile entity = getAttachment(id, accessKey);
        Resource resource;
        try {
            resource = storageResolver.load(entity.getStoragePath());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件读取失败");
        }
        AttachmentPresentation presentation = toPresentation(entity, null);
        return new AttachmentDownloadPayload(
                presentation.fileName(),
                resolveResponseContentType(entity, presentation.previewType()),
                resource,
                presentation.previewSupported(),
                presentation.previewType()
        );
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadPayload loadForPreview(Long id, String accessKey) {
        AttachmentDownloadPayload payload = loadForDownload(id, accessKey);
        if (!Boolean.TRUE.equals(payload.previewSupported())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前附件不支持预览");
        }
        return payload;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件超过大小限制");
        }
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

    private String extractFileName(String candidateFileName) {
        int slashIndex = candidateFileName.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < candidateFileName.length() - 1) {
            return candidateFileName.substring(slashIndex + 1);
        }
        return candidateFileName;
    }

    private String normalizeOriginalFileName(MultipartFile file, String sourceType) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            String baseName = SOURCE_CLIPBOARD.equals(sourceType) ? "clipboard" : "upload";
            AttachmentFilenameResolver.FilenameParts parts = filenameResolver.parseFilenameParts("", file.getContentType());
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

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private AttachmentView toResponse(AttachmentFile entity, String moduleKey) {
        AttachmentPresentation presentation = toPresentation(entity, moduleKey);
        return new AttachmentView(
                presentation.id(),
                presentation.name(),
                presentation.fileName(),
                presentation.originalFileName(),
                presentation.contentType(),
                presentation.fileSize(),
                presentation.sourceType(),
                presentation.uploader(),
                presentation.uploadTime(),
                presentation.previewSupported(),
                presentation.previewType(),
                presentation.previewUrl(),
                presentation.downloadUrl()
        );
    }

    private AttachmentFile getAttachment(Long id, String accessKey) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .filter(item -> accessKeyMatches(item, accessKey))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在"));
    }

    private void cleanupStoredFileQuietly(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            storageResolver.delete(storagePath);
        } catch (Exception ignored) {
            // Best-effort cleanup only. The original persistence error should be preserved.
        }
    }

    private AttachmentPresentation toPresentation(AttachmentFile entity, String moduleKey) {
        String previewType = detectPreviewType(entity);
        boolean previewSupported = !"none".equals(previewType);
        String baseUrl = "/api/attachments/" + entity.getId();
        String accessKey = urlEncode(entity.getAccessKey());
        String moduleQuery = toModuleQuery(moduleKey);
        return new AttachmentPresentation(
                entity.getId(),
                entity.getOriginalFileName(),
                entity.getFileName(),
                entity.getOriginalFileName(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getSourceType(),
                entity.getCreatedName(),
                entity.getCreatedAt(),
                previewSupported,
                previewType,
                previewSupported ? baseUrl + "/preview?accessKey=" + accessKey + moduleQuery : null,
                baseUrl + "/download?accessKey=" + accessKey + moduleQuery
        );
    }

    private String toModuleQuery(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            return "";
        }
        return "&moduleKey=" + urlEncode(moduleKey.trim());
    }

    private String detectPreviewType(AttachmentFile entity) {
        String extension = normalizedExtension(entity);
        if ("pdf".equals(extension)) {
            return "pdf";
        }
        if (List.of("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(extension)) {
            return "image";
        }
        return "none";
    }

    private String resolveResponseContentType(AttachmentFile entity, String previewType) {
        return switch (previewType) {
            case "pdf" -> "application/pdf";
            case "image" -> switch (normalizedExtension(entity)) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "bmp" -> "image/bmp";
                default -> "application/octet-stream";
            };
                default -> "application/octet-stream";
        };
    }

    private boolean accessKeyMatches(AttachmentFile entity, String accessKey) {
        if (accessKey == null || accessKey.isBlank() || entity.getAccessKey() == null || entity.getAccessKey().isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                entity.getAccessKey().getBytes(StandardCharsets.UTF_8),
                accessKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String generateAccessKey() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizedExtension(AttachmentFile entity) {
        return entity.getFileExtension() == null ? "" : entity.getFileExtension().trim().toLowerCase(Locale.ROOT);
    }

    private record AttachmentPresentation(
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
    }

    public record AttachmentDownloadPayload(
            String fileName,
            String contentType,
            Resource resource,
            Boolean previewSupported,
            String previewType
    ) {
    }
}
