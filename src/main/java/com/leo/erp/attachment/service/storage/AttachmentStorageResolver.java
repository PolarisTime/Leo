package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.oss.service.OssSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AttachmentStorageResolver {

    private final Map<String, AttachmentStorage> storageByType;
    private final AttachmentProperties properties;
    private final OssSettingService ossSettingService;

    public AttachmentStorageResolver(List<AttachmentStorage> storages, AttachmentProperties properties) {
        this(storages, properties, null);
    }

    @Autowired
    public AttachmentStorageResolver(List<AttachmentStorage> storages,
                                     AttachmentProperties properties,
                                     OssSettingService ossSettingService) {
        this.properties = properties;
        this.ossSettingService = ossSettingService;
        this.storageByType = new HashMap<>();
        for (AttachmentStorage storage : storages) {
            this.storageByType.put(storage.type(), storage);
        }
    }

    public String store(String objectKey, MultipartFile file) throws IOException {
        return getConfiguredStorage().store(objectKey, file);
    }

    public String storeBytes(String objectKey, byte[] content, String contentType) throws IOException {
        return getConfiguredStorage().storeBytes(objectKey, content, contentType);
    }

    public DirectUploadAttachmentStorage.PresignedUpload prepareDirectUpload(
            String objectKey, String contentType, long fileSize, String sha256Hex) {
        return getConfiguredDirectUploadStorage().prepareDirectUpload(objectKey, contentType, fileSize, sha256Hex);
    }

    public void verifyDirectUpload(String storagePath, long expectedFileSize, String expectedSha256Hex) {
        resolveDirectUploadByStoragePath(storagePath).verifyDirectUpload(storagePath, expectedFileSize, expectedSha256Hex);
    }

    public URI createPresignedAccessUrl(String storagePath, String fileName, String contentType, boolean inline) {
        AttachmentStorage storage = resolveByStoragePath(storagePath);
        if (storage instanceof DirectUploadAttachmentStorage directStorage) {
            return directStorage.createPresignedAccessUrl(storagePath, fileName, contentType, inline);
        }
        return null;
    }

    public Resource load(String storagePath) throws IOException {
        return resolveByStoragePath(storagePath).load(storagePath);
    }

    public void delete(String storagePath) throws IOException {
        resolveByStoragePath(storagePath).delete(storagePath);
    }

    private AttachmentStorage getConfiguredStorage() {
        String configuredType = normalizedType(ossSettingService == null
                ? properties.getStorage().getType()
                : ossSettingService.resolveRuntimeSetting().storageType());
        AttachmentStorage storage = storageByType.get(configuredType);
        if (storage == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未配置可用的附件存储后端: " + configuredType);
        }
        return storage;
    }

    private DirectUploadAttachmentStorage getConfiguredDirectUploadStorage() {
        AttachmentStorage storage = getConfiguredStorage();
        if (storage instanceof DirectUploadAttachmentStorage directStorage) {
            return directStorage;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前附件存储不支持直传");
    }

    private DirectUploadAttachmentStorage resolveDirectUploadByStoragePath(String storagePath) {
        AttachmentStorage storage = resolveByStoragePath(storagePath);
        if (storage instanceof DirectUploadAttachmentStorage directStorage) {
            return directStorage;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前附件存储不支持直传");
    }

    private AttachmentStorage resolveByStoragePath(String storagePath) {
        if (storagePath != null) {
            int colonIndex = storagePath.indexOf(':');
            if (colonIndex > 0) {
                String type = normalizedType(storagePath.substring(0, colonIndex));
                AttachmentStorage storage = storageByType.get(type);
                if (storage != null) {
                    return storage;
                }
            }
        }
        AttachmentStorage local = storageByType.get("local");
        if (local != null) {
            return local;
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法解析附件存储后端");
    }

    private String normalizedType(String type) {
        if (type == null || type.isBlank()) {
            return "local";
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
