package com.leo.erp.system.oss.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.service.storage.S3ClientProvider;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.oss.domain.entity.OssSetting;
import com.leo.erp.system.oss.repository.OssSettingRepository;
import com.leo.erp.system.oss.web.dto.OssCorsConfigureRequest;
import com.leo.erp.system.oss.web.dto.OssOperationResult;
import com.leo.erp.system.oss.web.dto.OssSettingRequest;
import com.leo.erp.system.oss.web.dto.OssSettingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import software.amazon.awssdk.core.ClientEndpointProvider;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.protocols.xml.AwsS3ProtocolFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.transform.PutBucketCorsRequestMarshaller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class OssSettingService {

    private static final String STORAGE_MODE_SERVER_S3 = "server-s3";
    private static final String STORAGE_MODE_SERVER_LOCAL = "server-local";

    private final OssSettingRepository repository;
    private final SnowflakeIdGenerator idGenerator;
    private final AttachmentProperties attachmentProperties;
    private final OssSecretCryptor secretCryptor;
    private final S3ClientProvider s3ClientProvider;

    public OssSettingService(OssSettingRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             AttachmentProperties attachmentProperties,
                             OssSecretCryptor secretCryptor) {
        this(repository, idGenerator, attachmentProperties, secretCryptor, null);
    }

    @Autowired
    public OssSettingService(OssSettingRepository repository,
                             SnowflakeIdGenerator idGenerator,
                             AttachmentProperties attachmentProperties,
                             OssSecretCryptor secretCryptor,
                             S3ClientProvider s3ClientProvider) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.attachmentProperties = attachmentProperties;
        this.secretCryptor = secretCryptor;
        this.s3ClientProvider = s3ClientProvider;
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

    @Transactional(readOnly = true)
    public OssOperationResult testStorage(OssSettingRequest request) {
        ResolvedOssSetting runtime = resolveRuntimeSetting(request);
        if (!"s3".equals(runtime.storageType())) {
            return new OssOperationResult(
                    true,
                    "LOCAL",
                    "当前为后端本机存储，无需测试 OSS 连通性",
                    null,
                    List.of("本机目录: " + runtime.localPath())
            );
        }
        AttachmentProperties.S3 s3 = runtime.s3();
        S3Client s3Client = requireS3ClientProvider().getClient(s3);
        String objectKey = runtime.keyPrefix() + "/diagnostics/" + Instant.now().toEpochMilli()
                + "-" + UUID.randomUUID() + ".txt";
        byte[] content = "leo-oss-diagnostics".getBytes(StandardCharsets.UTF_8);
        List<String> details = new ArrayList<>();
        details.add("Bucket: " + s3.getBucket());
        details.add("Endpoint: " + s3.getEndpoint());
        details.add("ObjectKey: " + objectKey);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3.getBucket())
                            .key(objectKey)
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromBytes(content)
            );
            details.add("写入测试对象成功");
        } catch (S3Exception ex) {
            throw ossFailure("WRITE", "OSS 写入测试失败", ex);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OSS 写入测试失败: " + safeMessage(ex));
        }
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder().bucket(s3.getBucket()).key(objectKey).build()
        )) {
            byte[] readBytes = response.readAllBytes();
            if (!java.util.Arrays.equals(content, readBytes)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "OSS 读取测试失败: 读取内容与写入内容不一致");
            }
            details.add("读取测试对象成功");
        } catch (S3Exception ex) {
            throw ossFailure("READ", "OSS 读取测试失败", ex);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OSS 读取测试失败: 响应读取异常");
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(s3.getBucket()).key(objectKey).build());
            details.add("删除测试对象成功");
        } catch (S3Exception ex) {
            throw ossFailure("DELETE", "OSS 删除测试失败", ex);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OSS 删除测试失败: " + safeMessage(ex));
        }
        return new OssOperationResult(true, "DELETE", "OSS 存储读写删除测试通过", objectKey, details);
    }

    @Transactional(readOnly = true)
    public OssOperationResult configureCors(OssCorsConfigureRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "CORS 配置请求不能为空");
        }
        ResolvedOssSetting runtime = resolveRuntimeSetting(request.setting());
        if (!"s3".equals(runtime.storageType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前为后端本机存储，无需配置 OSS CORS");
        }
        String origin = normalizeOrigin(request.origin());
        AttachmentProperties.S3 s3 = runtime.s3();
        S3Client s3Client = requireS3ClientProvider().getClient(s3);
        List<String> methods = normalizeCorsMethods(request.methods());
        CORSRule rule = CORSRule.builder()
                .id("leo-erp-attachment-access")
                .allowedOrigins(origin)
                .allowedMethods(methods)
                .allowedHeaders("*")
                .exposeHeaders("ETag", "x-amz-request-id")
                .maxAgeSeconds(3600)
                .build();
        CORSConfiguration corsConfiguration = CORSConfiguration.builder().corsRules(rule).build();
        try {
            s3Client.putBucketCors(buildPutBucketCorsRequest(s3.getBucket(), s3.getEndpoint(), corsConfiguration));
        } catch (S3Exception ex) {
            throw ossFailure("CORS", "OSS CORS 配置失败", ex);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OSS CORS 配置失败: " + safeMessage(ex));
        }
        return new OssOperationResult(
                true,
                "CORS",
                "OSS CORS 配置完成",
                null,
                List.of(
                        "Bucket: " + s3.getBucket(),
                        "Origin: " + origin,
                        "Methods: " + String.join(",", methods)
                )
        );
    }

    @Transactional
    public OssSettingResponse save(OssSettingRequest request) {
        NormalizedOssSetting normalized = normalizeRequest(request);
        boolean s3Mode = STORAGE_MODE_SERVER_S3.equals(normalized.storageMode());

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

        setting.setStorageMode(normalized.storageMode());
        setting.setProvider(normalized.provider());
        setting.setEndpoint(normalized.endpoint());
        setting.setBucket(normalized.bucket());
        setting.setRegion(normalized.region());
        setting.setAccessKey(normalized.accessKey());
        setting.setKeyPrefix(normalized.keyPrefix());
        setting.setPathStyleAccess(normalized.pathStyleAccess());
        setting.setEncryptedStorage(normalized.encryptedStorage());
        setting.setServerProxyOnly(normalized.serverProxyOnly());
        setting.setStatus(StatusConstants.NORMAL);
        setting.setRemark("系统设置页面维护");
        return toResponse(repository.save(setting));
    }

    private ResolvedOssSetting resolveRuntimeSetting(OssSettingRequest request) {
        if (request == null) {
            return resolveRuntimeSetting();
        }
        NormalizedOssSetting normalized = normalizeRequest(request);
        if (!STORAGE_MODE_SERVER_S3.equals(normalized.storageMode())) {
            return new ResolvedOssSetting(
                    "local",
                    normalized.keyPrefix(),
                    valueOrDefault(attachmentProperties.getStorage().getLocal().getPath(), "/tmp/leo/uploads"),
                    attachmentProperties.getStorage().getS3(),
                    normalized.encryptedStorage(),
                    normalized.serverProxyOnly()
            );
        }
        AttachmentProperties.S3 s3 = copyBaseS3Config();
        s3.setEndpoint(normalized.endpoint());
        s3.setRegion(normalized.region());
        s3.setBucket(normalized.bucket());
        s3.setAccessKey(normalized.accessKey());
        s3.setSecretKey(resolveSecretForOperation(request.secretKey()));
        s3.setPathStyleAccess(normalized.pathStyleAccess());
        s3.setEncryptedStorage(normalized.encryptedStorage());
        s3.setServerProxyOnly(normalized.serverProxyOnly());
        return new ResolvedOssSetting(
                "s3",
                normalized.keyPrefix(),
                valueOrDefault(attachmentProperties.getStorage().getLocal().getPath(), "/tmp/leo/uploads"),
                s3,
                normalized.encryptedStorage(),
                normalized.serverProxyOnly()
        );
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
                s3.isEncryptedStorage(),
                s3.isServerProxyOnly()
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
        s3.setEncryptedStorage(setting.isEncryptedStorage());
        s3.setServerProxyOnly(setting.isServerProxyOnly());
        return new ResolvedOssSetting(
                "s3",
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
                storage.getS3().isEncryptedStorage(),
                storage.getS3().isServerProxyOnly()
        );
    }

    private AttachmentProperties.S3 copyBaseS3Config() {
        AttachmentProperties.S3 source = attachmentProperties.getStorage().getS3();
        AttachmentProperties.S3 target = new AttachmentProperties.S3();
        target.setConnectTimeout(source.getConnectTimeout());
        target.setReadTimeout(source.getReadTimeout());
        target.setPresignUploadTtl(source.getPresignUploadTtl());
        target.setPresignPreviewTtl(source.getPresignPreviewTtl());
        target.setEncryptedStorage(source.isEncryptedStorage());
        target.setServerProxyOnly(source.isServerProxyOnly());
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

    private NormalizedOssSetting normalizeRequest(OssSettingRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OSS 设置不能为空");
        }
        String storageMode = normalizeStorageMode(request.storageMode());
        String provider = normalizeProvider(request.provider());
        boolean s3Mode = STORAGE_MODE_SERVER_S3.equals(storageMode);
        return new NormalizedOssSetting(
                storageMode,
                provider,
                s3Mode ? normalizeEndpoint(request.endpoint()) : normalizeOptional(request.endpoint()),
                s3Mode ? normalizeRequired(request.bucket(), "Bucket") : normalizeOptional(request.bucket()),
                s3Mode ? normalizeRequired(request.region(), "Region") : normalizeOptional(request.region()),
                s3Mode ? normalizeRequired(request.accessKey(), "Access Key") : normalizeOptional(request.accessKey()),
                normalizeKeyPrefix(request.keyPrefix()),
                Boolean.TRUE.equals(request.pathStyleAccess()),
                Boolean.TRUE.equals(request.encryptedStorage()),
                !Boolean.FALSE.equals(request.serverProxyOnly())
        );
    }

    private String resolveSecretForOperation(String requestSecretKey) {
        if (requestSecretKey != null && !requestSecretKey.isBlank()) {
            return requestSecretKey.trim();
        }
        OssSetting existing = repository.findFirstByDeletedFlagFalseOrderByIdAsc().orElse(null);
        if (existing == null || existing.getEncryptedSecretKey() == null || existing.getEncryptedSecretKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "测试或配置 OSS 前请填写 Secret Key，或先保存已包含 Secret Key 的 OSS 设置");
        }
        return secretCryptor.decrypt(existing.getEncryptedSecretKey());
    }

    private List<String> normalizeCorsMethods(List<String> methods) {
        List<String> normalized = new ArrayList<>();
        if (methods != null) {
            for (String method : methods) {
                if (method == null || method.isBlank()) {
                    continue;
                }
                String value = method.trim().toUpperCase(Locale.ROOT);
                if (!List.of("GET", "PUT", "POST", "DELETE", "HEAD").contains(value)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 CORS 请求方法: " + value);
                }
                if (!normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(List.of("GET", "PUT", "HEAD"));
        }
        if (!normalized.contains("HEAD")) {
            normalized.add("HEAD");
        }
        return normalized;
    }

    private PutBucketCorsRequest buildPutBucketCorsRequest(String bucket,
                                                           String endpoint,
                                                           CORSConfiguration corsConfiguration) {
        PutBucketCorsRequest request = PutBucketCorsRequest.builder()
                .bucket(bucket)
                .corsConfiguration(corsConfiguration)
                .build();
        return request.toBuilder()
                .contentMD5(calculatePutBucketCorsContentMd5(endpoint, request))
                .build();
    }

    private String calculatePutBucketCorsContentMd5(String endpoint, PutBucketCorsRequest request) {
        SdkClientConfiguration clientConfiguration = SdkClientConfiguration.builder()
                .option(
                        SdkClientOption.CLIENT_ENDPOINT_PROVIDER,
                        ClientEndpointProvider.forEndpointOverride(URI.create(normalizeEndpoint(endpoint)))
                )
                .build();
        AwsS3ProtocolFactory protocolFactory = AwsS3ProtocolFactory.builder()
                .clientConfiguration(clientConfiguration)
                .build();
        byte[] body = readCorsBody(new PutBucketCorsRequestMarshaller(protocolFactory)
                .marshall(request)
                .contentStreamProvider());
        return Base64.getEncoder().encodeToString(DigestUtils.md5Digest(body));
    }

    byte[] readCorsBody(Optional<ContentStreamProvider> bodyProvider) {
        return bodyProvider
                .map(this::readCorsBody)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "OSS CORS 请求体为空"));
    }

    byte[] readCorsBody(ContentStreamProvider bodyProvider) {
        try {
            return bodyProvider.newStream().readAllBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 OSS CORS Content-MD5 失败: 请求体读取异常");
        }
    }

    private String normalizeOrigin(String origin) {
        String normalized = normalizeRequired(origin, "前端访问源");
        if ("*".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "出于安全限制，CORS 一键配置不允许使用 *，请填写明确的前端域名");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            String rawPath = valueOrEmpty(uri.getRawPath());
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || !rawPath.isBlank() && !"/".equals(rawPath)
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("invalid origin");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "前端访问源格式错误，应为协议+域名+可选端口，例如 https://erp.example.com");
        }
        return normalized;
    }

    private S3ClientProvider requireS3ClientProvider() {
        if (s3ClientProvider == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 客户端未配置");
        }
        return s3ClientProvider;
    }

    private BusinessException ossFailure(String stage, String message, S3Exception ex) {
        String requestId = ex.requestId() == null ? "" : "，RequestId: " + ex.requestId();
        String errorMessage = ex.awsErrorDetails() == null ? safeMessage(ex) : ex.awsErrorDetails().errorMessage();
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                message + "（阶段: " + stage + "，HTTP " + ex.statusCode() + "，原因: " + errorMessage + requestId + "）"
        );
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
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

    private record NormalizedOssSetting(
            String storageMode,
            String provider,
            String endpoint,
            String bucket,
            String region,
            String accessKey,
            String keyPrefix,
            boolean pathStyleAccess,
            boolean encryptedStorage,
            boolean serverProxyOnly
    ) {
    }
}
