package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.oss.service.OssSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalAttachmentStorage implements AttachmentStorage {

    private static final String PREFIX = "local:";

    private final AttachmentProperties properties;
    private final OssSettingService ossSettingService;

    public LocalAttachmentStorage(AttachmentProperties properties) {
        this(properties, null);
    }

    @Autowired
    public LocalAttachmentStorage(AttachmentProperties properties, OssSettingService ossSettingService) {
        this.properties = properties;
        this.ossSettingService = ossSettingService;
    }

    @Override
    public String type() {
        return "local";
    }

    @Override
    public String store(String objectKey, MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return store(objectKey, inputStream);
        }
    }

    @Override
    public String storeBytes(String objectKey, byte[] content, String contentType) throws IOException {
        return store(objectKey, new java.io.ByteArrayInputStream(content));
    }

    private String store(String objectKey, InputStream inputStream) throws IOException {
        Path root = resolveRootPath();
        Path targetPath = root.resolve(objectKey).normalize();
        if (!targetPath.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法文件路径");
        }
        Files.createDirectories(targetPath.getParent());
        Files.copy(inputStream, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return PREFIX + objectKey;
    }

    @Override
    public Resource load(String storagePath) {
        Path root = resolveRootPath();
        Path targetPath = resolvePath(storagePath, root);
        if (!Files.exists(targetPath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
        }
        return new FileSystemResource(targetPath);
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Path root = resolveRootPath();
        Path targetPath = resolvePath(storagePath, root);
        Files.deleteIfExists(targetPath);
    }

    private Path resolveRootPath() {
        try {
            String localPath = ossSettingService == null
                    ? properties.getStorage().getLocal().getPath()
                    : ossSettingService.resolveRuntimeSetting().localPath();
            return Paths.get(localPath).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "本地附件存储路径配置错误");
        }
    }

    private Path resolvePath(String storagePath, Path root) {
        try {
            if (storagePath != null && storagePath.startsWith(PREFIX)) {
                Path resolved = root.resolve(storagePath.substring(PREFIX.length())).normalize();
                if (!resolved.startsWith(root)) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "本地附件存储路径非法");
                }
                return resolved;
            }

            Path legacyPath = Paths.get(storagePath).toAbsolutePath().normalize();
            if (!legacyPath.startsWith(root)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "本地附件存储路径非法");
            }
            return legacyPath;
        } catch (InvalidPathException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "本地附件存储路径损坏");
        }
    }
}
