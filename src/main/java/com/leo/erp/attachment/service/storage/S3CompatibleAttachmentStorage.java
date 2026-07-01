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
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class S3CompatibleAttachmentStorage implements AttachmentStorage {

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

    private String describeS3Error(S3Exception ex) {
        String message = ex.awsErrorDetails() == null ? ex.getMessage() : ex.awsErrorDetails().errorMessage();
        if (message == null || message.isBlank()) {
            message = ex.getMessage();
        }
        return "HTTP " + ex.statusCode() + " " + message;
    }
}
