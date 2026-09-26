package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.support.AttachmentMediaTypes;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AttachmentResponseAssembler {

    private static final String STORAGE_TYPE_LOCAL = "local";
    private static final String STORAGE_TYPE_S3 = "s3";

    public AttachmentView toResponse(AttachmentFile entity, String moduleKey) {
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
                presentation.downloadUrl(),
                presentation.storageType(),
                presentation.storageLabel()
        );
    }

    public AttachmentPresentation toPresentation(AttachmentFile entity, String moduleKey) {
        String previewType = detectPreviewType(entity);
        boolean previewSupported = !"none".equals(previewType);
        String baseUrl = "/api/v2.0/attachments/" + entity.getId();
        String accessKey = urlEncode(entity.getAccessKey());
        String moduleQuery = toModuleQuery(moduleKey);
        String storageType = resolveStorageType(entity.getStoragePath());
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
                previewSupported
                        ? baseUrl + "/content?disposition=inline&accessKey=" + accessKey + moduleQuery
                        : null,
                baseUrl + "/content?disposition=attachment&accessKey=" + accessKey + moduleQuery,
                storageType,
                resolveStorageLabel(storageType)
        );
    }

    public String detectPreviewType(AttachmentFile entity) {
        String extension = normalizedExtension(entity);
        if ("pdf".equals(extension)) {
            return "pdf";
        }
        if (List.of("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(extension)) {
            return "image";
        }
        return "none";
    }

    public String resolveResponseContentType(AttachmentFile entity, String previewType) {
        return switch (previewType) {
            case "pdf" -> AttachmentMediaTypes.PDF;
            case "image" -> AttachmentMediaTypes.contentTypeOfExtension(
                    normalizedExtension(entity));
            default -> AttachmentMediaTypes.OCTET_STREAM;
        };
    }

    private String resolveStorageType(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return STORAGE_TYPE_LOCAL;
        }
        int colonIndex = storagePath.indexOf(':');
        if (colonIndex <= 0) {
            return STORAGE_TYPE_LOCAL;
        }
        String type = storagePath.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
        return STORAGE_TYPE_S3.equals(type) ? STORAGE_TYPE_S3 : STORAGE_TYPE_LOCAL;
    }

    private String resolveStorageLabel(String storageType) {
        return STORAGE_TYPE_S3.equals(storageType) ? "S3存储" : "本机存储";
    }

    private String toModuleQuery(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            return "";
        }
        return "&moduleKey=" + urlEncode(moduleKey.trim());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizedExtension(AttachmentFile entity) {
        return entity.getFileExtension() == null ? "" : entity.getFileExtension().trim().toLowerCase(Locale.ROOT);
    }

    public record AttachmentPresentation(
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
            String downloadUrl,
            String storageType,
            String storageLabel
    ) {
    }
}
