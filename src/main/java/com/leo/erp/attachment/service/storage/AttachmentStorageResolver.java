package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AttachmentStorageResolver {

    private final Map<String, AttachmentStorage> storageByType;
    private final AttachmentProperties properties;

    public AttachmentStorageResolver(List<AttachmentStorage> storages, AttachmentProperties properties) {
        this.properties = properties;
        this.storageByType = new HashMap<>();
        for (AttachmentStorage storage : storages) {
            this.storageByType.put(storage.type(), storage);
        }
    }

    public String store(String objectKey, MultipartFile file) throws IOException {
        return getConfiguredStorage().store(objectKey, file);
    }

    public Resource load(String storagePath) throws IOException {
        return resolveByStoragePath(storagePath).load(storagePath);
    }

    public void delete(String storagePath) throws IOException {
        resolveByStoragePath(storagePath).delete(storagePath);
    }

    private AttachmentStorage getConfiguredStorage() {
        String configuredType = normalizedType(properties.getStorage().getType());
        AttachmentStorage storage = storageByType.get(configuredType);
        if (storage == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未配置可用的附件存储后端: " + configuredType);
        }
        return storage;
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
