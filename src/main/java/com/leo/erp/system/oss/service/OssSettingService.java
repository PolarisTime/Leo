package com.leo.erp.system.oss.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.oss.domain.entity.OssSetting;
import com.leo.erp.system.oss.repository.OssSettingRepository;
import com.leo.erp.system.oss.web.dto.OssSettingRequest;
import com.leo.erp.system.oss.web.dto.OssSettingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Locale;

@Service
public class OssSettingService {

    private static final String STORAGE_MODE_SERVER_S3 = "server-s3";
    private static final String STORAGE_MODE_SERVER_LOCAL = "server-local";

    private final OssSettingRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final AttachmentProperties attachmentProperties;
    private final OssSecretCryptor secretCryptor;

    public OssSettingService(OssSettingRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             AttachmentProperties attachmentProperties,
                             OssSecretCryptor secretCryptor) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.attachmentProperties = attachmentProperties;
        this.secretCryptor = secretCryptor;
    }

    @Transactional(readOnly = true)
    public OssSettingResponse current() {
        return repository.findFirstByDeletedFlagFalseOrderByIdAsc()
                .map(this::toResponse)
                .orElseGet(this::fromApplicationConfig);
    }

    @Transactional(readOnly = true)
    public ResolvedOssSetting resolveRuntimeSetting() {
        return repository.findFirstByDeletedFlagFalseOrderByIdAsc()
                .map(this::toRuntimeSetting)
                .orElseGet(this::runtimeFromApplicationConfig);
    }

    @Transactional
    public OssSettingResponse save(OssSettingRequest request) {
        String storageMode = normalizeStorageMode(request.storageMode());
        String provider = normalizeProvider(request.provider());
        boolean s3Mode = STORAGE_MODE_SERVER_S3.equals(storageMode);
        String endpoint = s3Mode ? normalizeEndpoint(request.endpoint()) : normalizeOptional(request.endpoint());
        String bucket = s3Mode ? normalizeRequired(request.bucket(), "Bucket") : normalizeOptional(request.bucket());
        String region = s3Mode ? normalizeRequired(request.region(), "Region") : normalizeOptional(request.region());
        String accessKey = s3Mode ? normalizeRequired(request.accessKey(), "Access Key") : normalizeOptional(request.accessKey());
        String keyPrefix = normalizeKeyPrefix(request.keyPrefix());

        OssSetting setting = repository.findFirstByDeletedFlagFalseOrderByIdAsc()
                .orElseGet(() -> {
                    OssSetting created = new OssSetting();
                    created.setId(idGenerator.nextId());
                    return created;
                });
        if (request.secretKey() != null && !request.secretKey().isBlank()) {
            setting.setEncryptedSecretKey(secretCryptor.encrypt(request.secretKey().trim()));
        } else if (s3Mode && (setting.getEncryptedSecretKey() == null || setting.getEncryptedSecretKey().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "首次保存 OSS 设置必须填写 Secret Key");
        }

        setting.setStorageMode(storageMode);
        setting.setProvider(provider);
        setting.setEndpoint(endpoint);
        setting.setBucket(bucket);
        setting.setRegion(region);
        setting.setAccessKey(accessKey);
        setting.setKeyPrefix(keyPrefix);
        setting.setPathStyleAccess(Boolean.TRUE.equals(request.pathStyleAccess()));
        setting.setEncryptedStorage(Boolean.TRUE.equals(request.encryptedStorage()));
        setting.setServerProxyOnly(!Boolean.FALSE.equals(request.serverProxyOnly()));
        setting.setStatus(StatusConstants.NORMAL);
        setting.setRemark("系统设置页面维护");
        return toResponse(repository.save(setting));
    }

    private OssSettingResponse toResponse(OssSetting setting) {
        return new OssSettingResponse(
                setting.getStorageMode(),
                setting.getProvider(),
                setting.getEndpoint(),
                setting.getBucket(),
                setting.getRegion(),
                setting.getAccessKey(),
                setting.getEncryptedSecretKey() != null && !setting.getEncryptedSecretKey().isBlank(),
                setting.getKeyPrefix(),
                setting.isPathStyleAccess(),
                setting.isEncryptedStorage(),
                setting.isServerProxyOnly()
        );
    }

    private OssSettingResponse fromApplicationConfig() {
        AttachmentProperties.Storage storage = attachmentProperties.getStorage();
        AttachmentProperties.S3 s3 = storage.getS3();
        return new OssSettingResponse(
                "s3".equalsIgnoreCase(storage.getType()) ? STORAGE_MODE_SERVER_S3 : STORAGE_MODE_SERVER_LOCAL,
                "s3-compatible",
                valueOrEmpty(s3.getEndpoint()),
                valueOrEmpty(s3.getBucket()),
                valueOrEmpty(s3.getRegion()),
                valueOrEmpty(s3.getAccessKey()),
                s3.getSecretKey() != null && !s3.getSecretKey().isBlank(),
                valueOrDefault(storage.getKeyPrefix(), "attachments"),
                s3.isPathStyleAccess(),
                false,
                true
        );
    }

    private ResolvedOssSetting toRuntimeSetting(OssSetting setting) {
        if (!STORAGE_MODE_SERVER_S3.equals(setting.getStorageMode())) {
            return new ResolvedOssSetting(
                    "local",
                    normalizeKeyPrefix(setting.getKeyPrefix()),
                    valueOrDefault(attachmentProperties.getStorage().getLocal().getPath(), "/tmp/leo/uploads"),
                    attachmentProperties.getStorage().getS3(),
                    setting.isEncryptedStorage(),
                    setting.isServerProxyOnly()
            );
        }
        AttachmentProperties.S3 s3 = copyBaseS3Config();
        s3.setEndpoint(setting.getEndpoint());
        s3.setRegion(setting.getRegion());
        s3.setBucket(setting.getBucket());
        s3.setAccessKey(setting.getAccessKey());
        s3.setSecretKey(secretCryptor.decrypt(setting.getEncryptedSecretKey()));
        s3.setPathStyleAccess(setting.isPathStyleAccess());
        return new ResolvedOssSetting(
                STORAGE_MODE_SERVER_S3.equals(setting.getStorageMode()) ? "s3" : "local",
                normalizeKeyPrefix(setting.getKeyPrefix()),
                valueOrDefault(attachmentProperties.getStorage().getLocal().getPath(), "/tmp/leo/uploads"),
                s3,
                setting.isEncryptedStorage(),
                setting.isServerProxyOnly()
        );
    }

    private ResolvedOssSetting runtimeFromApplicationConfig() {
        AttachmentProperties.Storage storage = attachmentProperties.getStorage();
        return new ResolvedOssSetting(
                normalizeStorageType(storage.getType()),
                normalizeKeyPrefix(storage.getKeyPrefix()),
                valueOrDefault(storage.getLocal().getPath(), "/tmp/leo/uploads"),
                storage.getS3(),
                false,
                true
        );
    }

    private AttachmentProperties.S3 copyBaseS3Config() {
        AttachmentProperties.S3 source = attachmentProperties.getStorage().getS3();
        AttachmentProperties.S3 target = new AttachmentProperties.S3();
        target.setConnectTimeout(source.getConnectTimeout());
        target.setReadTimeout(source.getReadTimeout());
        target.setPresignUploadTtl(source.getPresignUploadTtl());
        target.setPresignPreviewTtl(source.getPresignPreviewTtl());
        return target;
    }

    private String normalizeStorageMode(String storageMode) {
        String normalized = normalizeRequired(storageMode, "存储模式").toLowerCase(Locale.ROOT);
        if (!STORAGE_MODE_SERVER_S3.equals(normalized) && !STORAGE_MODE_SERVER_LOCAL.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的存储模式");
        }
        return normalized;
    }

    private String normalizeProvider(String provider) {
        return normalizeRequired(provider, "服务商").toLowerCase(Locale.ROOT);
    }

    private String normalizeEndpoint(String endpoint) {
        String normalized = normalizeRequired(endpoint, "Endpoint");
        try {
            URI uri = URI.create(normalized);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("missing scheme or host");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Endpoint 格式错误");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "不能为空");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeStorageType(String storageType) {
        return "s3".equalsIgnoreCase(storageType) ? "s3" : "local";
    }

    private String normalizeKeyPrefix(String keyPrefix) {
        String normalized = valueOrDefault(keyPrefix, "attachments").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public record ResolvedOssSetting(
            String storageType,
            String keyPrefix,
            String localPath,
            AttachmentProperties.S3 s3,
            boolean encryptedStorage,
            boolean serverProxyOnly
    ) {
    }
}
