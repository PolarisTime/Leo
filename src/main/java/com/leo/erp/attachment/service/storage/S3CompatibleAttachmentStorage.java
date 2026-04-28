package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

@Component
public class S3CompatibleAttachmentStorage implements AttachmentStorage {

    private final AttachmentProperties properties;
    private final S3RequestExecutor requestExecutor;
    private final S3ChecksumUtil checksumUtil;
    private final S3PathParser pathParser;
    private final S3Signer signer;

    @Autowired
    public S3CompatibleAttachmentStorage(
            AttachmentProperties properties,
            S3RequestExecutor requestExecutor,
            S3ChecksumUtil checksumUtil,
            S3PathParser pathParser,
            S3Signer signer) {
        this.properties = properties;
        this.requestExecutor = requestExecutor;
        this.checksumUtil = checksumUtil;
        this.pathParser = pathParser;
        this.signer = signer;
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
        String payloadHash;
        try (InputStream in = Files.newInputStream(tempFile)) {
            payloadHash = checksumUtil.hexSha256(in);
        }
        HttpRequest request = signer.signedRequest(
                "PUT", objectKey, payloadHash, file.getContentType(), s3,
                HttpRequest.BodyPublishers.ofFile(tempFile));
        try {
            S3RequestExecutor.S3Response response = requestExecutor.execute(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("S3 上传失败: HTTP " + response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8));
            }
            return pathParser.buildStoragePath(s3.getBucket(), objectKey);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 上传被中断", ex);
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
        HttpRequest request = signer.signedRequest(
                "GET", parsed.objectKey(), checksumUtil.emptyBodyHash(), null, s3,
                HttpRequest.BodyPublishers.noBody());
        try {
            S3RequestExecutor.S3StreamResponse response = requestExecutor.executeForStream(request);
            if (response.statusCode() == 404) {
                response.close();
                throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (response; InputStream body = response.body()) {
                    throw new IOException("S3 下载失败: HTTP " + response.statusCode() + " " + new String(body.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return new InputStreamResource(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 下载被中断", ex);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        S3PathParser.ParsedStoragePath parsed = pathParser.parseStoragePath(storagePath);
        AttachmentProperties.S3 s3 = requireS3Config();
        if (!parsed.bucket().equals(s3.getBucket())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "当前 S3 配置与附件桶不一致");
        }
        HttpRequest request = signer.signedRequest(
                "DELETE", parsed.objectKey(), checksumUtil.emptyBodyHash(), null, s3,
                HttpRequest.BodyPublishers.noBody());
        try {
            S3RequestExecutor.S3Response response = requestExecutor.execute(request);
            if (response.statusCode() == 404) {
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("S3 删除失败: HTTP " + response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 删除被中断", ex);
        }
    }

    private AttachmentProperties.S3 requireS3Config() {
        AttachmentProperties.S3 s3 = properties.getStorage().getS3();
        if (pathParser.isBlank(s3.getEndpoint()) || pathParser.isBlank(s3.getBucket())
                || pathParser.isBlank(s3.getAccessKey()) || pathParser.isBlank(s3.getSecretKey())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 附件存储配置不完整");
        }
        return s3;
    }
}
