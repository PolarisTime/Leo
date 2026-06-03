package com.leo.erp.attachment.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        AttachmentProperties properties = new AttachmentProperties();
        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(20));
        assertThat(properties.getStorage()).isNotNull();
        assertThat(properties.getStorage().getType()).isEqualTo("local");
        assertThat(properties.getStorage().getKeyPrefix()).isEqualTo("attachments");
        assertThat(properties.getStorage().getLocal()).isNotNull();
        assertThat(properties.getStorage().getLocal().getPath()).isEqualTo("/tmp/leo/uploads");
        assertThat(properties.getStorage().getS3()).isNotNull();
    }

    @Test
    void shouldSetMaxFileSize() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(50));
        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(50));
    }

    @Test
    void shouldSetStorageProperties() {
        AttachmentProperties properties = new AttachmentProperties();
        AttachmentProperties.Storage storage = new AttachmentProperties.Storage();
        storage.setType("s3");
        storage.setKeyPrefix("my-prefix");
        properties.setStorage(storage);

        assertThat(properties.getStorage().getType()).isEqualTo("s3");
        assertThat(properties.getStorage().getKeyPrefix()).isEqualTo("my-prefix");
    }

    @Test
    void shouldSetLocalStoragePath() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().getLocal().setPath("/custom/path");
        assertThat(properties.getStorage().getLocal().getPath()).isEqualTo("/custom/path");
    }

    @Test
    void shouldSetS3Properties() {
        AttachmentProperties properties = new AttachmentProperties();
        AttachmentProperties.S3 s3 = properties.getStorage().getS3();
        s3.setEndpoint("http://localhost:9000");
        s3.setRegion("us-west-2");
        s3.setBucket("my-bucket");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setPathStyleAccess(false);
        s3.setConnectTimeout(Duration.ofSeconds(5));
        s3.setReadTimeout(Duration.ofSeconds(15));

        assertThat(s3.getEndpoint()).isEqualTo("http://localhost:9000");
        assertThat(s3.getRegion()).isEqualTo("us-west-2");
        assertThat(s3.getBucket()).isEqualTo("my-bucket");
        assertThat(s3.getAccessKey()).isEqualTo("access-key");
        assertThat(s3.getSecretKey()).isEqualTo("secret-key");
        assertThat(s3.isPathStyleAccess()).isFalse();
        assertThat(s3.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(s3.getReadTimeout()).isEqualTo(Duration.ofSeconds(15));
    }
}