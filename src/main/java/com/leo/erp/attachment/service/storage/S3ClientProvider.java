package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Component
public class S3ClientProvider implements DisposableBean {

    private volatile S3Client client;
    private volatile S3Presigner presigner;
    private volatile String clientFingerprint;
    private volatile String presignerFingerprint;

    public S3Client getClient(AttachmentProperties.S3 s3) {
        String fingerprint = fingerprint(s3);
        S3Client current = client;
        if (current != null && fingerprint.equals(clientFingerprint)) {
            return current;
        }
        synchronized (this) {
            if (client == null || !fingerprint.equals(clientFingerprint)) {
                closeQuietly(client);
                client = buildClient(s3);
                clientFingerprint = fingerprint;
            }
            return client;
        }
    }

    public S3Presigner getPresigner(AttachmentProperties.S3 s3) {
        String fingerprint = fingerprint(s3);
        S3Presigner current = presigner;
        if (current != null && fingerprint.equals(presignerFingerprint)) {
            return current;
        }
        synchronized (this) {
            if (presigner == null || !fingerprint.equals(presignerFingerprint)) {
                closeQuietly(presigner);
                presigner = buildPresigner(s3);
                presignerFingerprint = fingerprint;
            }
            return presigner;
        }
    }

    @Override
    public void destroy() {
        S3Client current = client;
        if (current != null) {
            current.close();
        }
        S3Presigner currentPresigner = presigner;
        if (currentPresigner != null) {
            currentPresigner.close();
        }
    }

    private String fingerprint(AttachmentProperties.S3 s3) {
        return String.join("|",
                value(s3.getEndpoint()),
                value(s3.getRegion()),
                value(s3.getBucket()),
                value(s3.getAccessKey()),
                value(s3.getSecretKey()),
                String.valueOf(s3.isPathStyleAccess()),
                String.valueOf(s3.getConnectTimeout()),
                String.valueOf(s3.getReadTimeout())
        );
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best-effort close before replacing a cached S3 client.
        }
    }

    private S3Client buildClient(AttachmentProperties.S3 s3) {
        URI endpoint = parseEndpoint(s3.getEndpoint());
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())
                ))
                .region(Region.of(s3.getRegion()))
                .endpointOverride(endpoint)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(s3.getConnectTimeout())
                        .socketTimeout(s3.getReadTimeout()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .checksumValidationEnabled(false)
                        .build())
                .build();
    }

    private S3Presigner buildPresigner(AttachmentProperties.S3 s3) {
        URI endpoint = parseEndpoint(s3.getEndpoint());
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())
                ))
                .region(Region.of(s3.getRegion()))
                .endpointOverride(endpoint)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .build())
                .build();
    }

    private URI parseEndpoint(String endpoint) {
        try {
            return URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "S3 Endpoint 配置错误");
        }
    }
}
