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

    public S3Client getClient(AttachmentProperties.S3 s3) {
        S3Client current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = buildClient(s3);
            }
            return client;
        }
    }

    public S3Presigner getPresigner(AttachmentProperties.S3 s3) {
        S3Presigner current = presigner;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (presigner == null) {
                presigner = buildPresigner(s3);
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
