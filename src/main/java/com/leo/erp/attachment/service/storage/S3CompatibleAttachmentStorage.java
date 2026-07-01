package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class S3CompatibleAttachmentStorage implements DirectUploadAttachmentStorage {

    private static final int HTTP_NOT_FOUND = 404;
    private final AttachmentProperties properties;
    private final S3ClientProvider clientProvider;
    private final S3PathParser pathParser;

    @Autowired
    public S3CompatibleAttachmentStorage(
            AttachmentProperties properties,
            S3ClientProvider clientProvider,
            S3PathParser pathParser) {
        this.properties = properties;
        this.clientProvider = clientProvider;
        this.pathParser = pathParser;
    }

    @Override
    public String type() {
        return "s3";
    }

    @Override
    public String store(String objectKey, MultipartFile file) throws IOException {
        AttachmentProperties.S3 s3 = requireS3Config();
        Path tempFile = Files.createTempFile("leo-s3-upload-", ".bin");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            S3Client s3Client = clientProvider.getClient(s3);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3.getBucket())
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(request, RequestBody.fromFile(tempFile));
            return pathParser.buildStoragePath(s3.getBucket(), objectKey);
        } catch (S3Exception ex) {
            throw new IOException("S3 上传失败: " + describeS3Error(ex), ex);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public Resource load(String storagePath) throws IOException {
        S3PathParser.ParsedStoragePath parsed = pathParser.parseStoragePath(storagePath);
        AttachmentProperties.S3 s3 = requireS3Config();
        if (!parsed.bucket().equals(s3.getBucket())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "当前 S3 配置与附件桶不一致");
        }
        try {
            S3Client s3Client = clientProvider.getClient(s3);
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(s3.getBucket())
                    .key(parsed.objectKey())
                    .build());
            return new InputStreamResource(response);
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
        } catch (S3Exception ex) {
            if (isNotFound(ex)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
            }
            throw new IOException("S3 下载失败: " + describeS3Error(ex), ex);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        S3PathParser.ParsedStoragePath parsed = pathParser.parseStoragePath(storagePath);
        AttachmentProperties.S3 s3 = requireS3Config();
        if (!parsed.bucket().equals(s3.getBucket())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "当前 S3 配置与附件桶不一致");
        }
        try {
            S3Client s3Client = clientProvider.getClient(s3);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3.getBucket())
                    .key(parsed.objectKey())
                    .build());
        } catch (NoSuchKeyException ex) {
            // S3 删除保持幂等：对象不存在视为已删除。
        } catch (S3Exception ex) {
            if (!isNotFound(ex)) {
                throw new IOException("S3 删除失败: " + describeS3Error(ex), ex);
            }
        }
    }

    @Override
    public PresignedUpload prepareDirectUpload(String objectKey, String contentType, long fileSize, String sha256Hex) {
        AttachmentProperties.S3 s3 = requireS3Config();
        S3Presigner presigner = clientProvider.getPresigner(s3);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(fileSize)
                .checksumSHA256(sha256Base64(sha256Hex))
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(uploadTtl(s3))
                .putObjectRequest(request)
                .build());
        return new PresignedUpload(
                URI.create(presigned.url().toString()),
                presigned.httpRequest().method().name(),
                signedHeaders(presigned.signedHeaders()),
                pathParser.buildStoragePath(s3.getBucket(), objectKey),
                presigned.expiration()
        );
    }

    @Override
    public void verifyDirectUpload(String storagePath, long expectedFileSize, String expectedSha256Hex) {
        S3PathParser.ParsedStoragePath parsed = pathParser.parseStoragePath(storagePath);
        AttachmentProperties.S3 s3 = requireS3Config();
        if (!parsed.bucket().equals(s3.getBucket())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "当前 S3 配置与附件桶不一致");
        }
        try {
            S3Client s3Client = clientProvider.getClient(s3);
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3.getBucket())
                    .key(parsed.objectKey())
                    .build());
            if (response.contentLength() != expectedFileSize) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "直传文件大小不一致");
            }
            String expectedChecksum = sha256Base64(expectedSha256Hex);
            String headChecksum = normalizeChecksum(response.checksumSHA256());
            if (!expectedChecksum.equals(headChecksum)
                    && !expectedChecksum.equals(readObjectSha256Base64(s3Client, s3, parsed.objectKey()))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "直传文件校验值不一致");
            }
        } catch (NoSuchKeyException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "直传文件不存在");
        } catch (S3Exception ex) {
            if (isNotFound(ex)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "直传文件不存在");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 直传校验失败");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 直传校验失败");
        }
    }

    @Override
    public URI createPresignedAccessUrl(String storagePath, String fileName, String contentType, boolean inline) {
        S3PathParser.ParsedStoragePath parsed = pathParser.parseStoragePath(storagePath);
        AttachmentProperties.S3 s3 = requireS3Config();
        if (!parsed.bucket().equals(s3.getBucket())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "当前 S3 配置与附件桶不一致");
        }
        S3Presigner presigner = clientProvider.getPresigner(s3);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(s3.getBucket())
                .key(parsed.objectKey())
                .responseContentType(contentType)
                .responseContentDisposition(contentDisposition(inline, fileName))
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(previewTtl(s3))
                .getObjectRequest(request)
                .build());
        return URI.create(presigned.url().toString());
    }

    private AttachmentProperties.S3 requireS3Config() {
        AttachmentProperties.S3 s3 = properties.getStorage().getS3();
        if (pathParser.isBlank(s3.getEndpoint()) || pathParser.isBlank(s3.getBucket())
                || pathParser.isBlank(s3.getRegion()) || pathParser.isBlank(s3.getAccessKey())
                || pathParser.isBlank(s3.getSecretKey())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 附件存储配置不完整");
        }
        return s3;
    }

    private boolean isNotFound(S3Exception ex) {
        return ex.statusCode() == HTTP_NOT_FOUND;
    }

    private Duration uploadTtl(AttachmentProperties.S3 s3) {
        return s3.getPresignUploadTtl() == null ? Duration.ofMinutes(10) : s3.getPresignUploadTtl();
    }

    private Duration previewTtl(AttachmentProperties.S3 s3) {
        return s3.getPresignPreviewTtl() == null ? Duration.ofMinutes(5) : s3.getPresignPreviewTtl();
    }

    private Map<String, String> signedHeaders(Map<String, java.util.List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                result.put(key, String.join(",", values));
            }
        });
        return result;
    }

    private String contentDisposition(boolean inline, String fileName) {
        String disposition = inline ? "inline" : "attachment";
        String safeFileName = fileName == null ? "download" : fileName.replace("\"", "");
        return String.format(Locale.ROOT, "%s; filename=\"%s\"", disposition, safeFileName);
    }

    private String describeS3Error(S3Exception ex) {
        String message = ex.awsErrorDetails() == null ? ex.getMessage() : ex.awsErrorDetails().errorMessage();
        if (message == null || message.isBlank()) {
            message = ex.getMessage();
        }
        return "HTTP " + ex.statusCode() + " " + message;
    }

    private String normalizeSha256Hex(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeChecksum(String value) {
        return value == null ? "" : value.trim();
    }

    private String readObjectSha256Base64(S3Client s3Client, AttachmentProperties.S3 s3, String objectKey)
            throws IOException {
        MessageDigest digest = sha256Digest();
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
                .bucket(s3.getBucket())
                .key(objectKey)
                .build())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = response.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 直传校验失败");
        }
    }

    private String sha256Base64(String sha256Hex) {
        String normalized = normalizeSha256Hex(sha256Hex);
        if (normalized.length() != 64) {
            return "";
        }
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            bytes[i] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
