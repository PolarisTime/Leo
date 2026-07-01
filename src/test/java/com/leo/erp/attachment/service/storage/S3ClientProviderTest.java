package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ClientProviderTest {

    @Test
    void shouldCreateAndReuseS3Client() {
        S3ClientProvider provider = new S3ClientProvider();

        S3Client first = provider.getClient(s3Config("http://127.0.0.1:9000"));
        S3Client second = provider.getClient(s3Config("http://127.0.0.1:9000"));

        assertThat(first).isSameAs(second);
        provider.destroy();
    }

    @Test
    void shouldDestroyBeforeClientIsCreated() {
        S3ClientProvider provider = new S3ClientProvider();

        provider.destroy();
    }

    @Test
    void shouldConvertInvalidEndpointToBusinessException() {
        S3ClientProvider provider = new S3ClientProvider();

        assertThatThrownBy(() -> provider.getClient(s3Config("://bad-endpoint")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 Endpoint 配置错误");
    }

    private AttachmentProperties.S3 s3Config(String endpoint) {
        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint(endpoint);
        s3.setRegion("us-east-1");
        s3.setBucket("test-bucket");
        s3.setAccessKey("minio");
        s3.setSecretKey("miniosecret");
        s3.setPathStyleAccess(true);
        return s3;
    }
}
