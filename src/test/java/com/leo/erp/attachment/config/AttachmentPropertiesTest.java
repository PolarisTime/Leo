package com.leo.erp.attachment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
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
        assertThat(properties.getStorage().getS3().isPathStyleAccess()).isFalse();
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

    @Test
    void shouldBindAttachmentStorageFromConfigurationProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("leo.attachment.max-file-size", "32MB")
                .withProperty("leo.attachment.storage.type", "s3")
                .withProperty("leo.attachment.storage.key-prefix", "uploads")
                .withProperty("leo.attachment.storage.local.path", "/data/uploads")
                .withProperty("leo.attachment.storage.s3.endpoint", "https://s3.example.test")
                .withProperty("leo.attachment.storage.s3.region", "ap-guangzhou")
                .withProperty("leo.attachment.storage.s3.bucket", "erp-attachments")
                .withProperty("leo.attachment.storage.s3.access-key", "access-key")
                .withProperty("leo.attachment.storage.s3.secret-key", "secret-key")
                .withProperty("leo.attachment.storage.s3.path-style-access", "false")
                .withProperty("leo.attachment.storage.s3.connect-timeout", "15s")
                .withProperty("leo.attachment.storage.s3.read-timeout", "45s")
                .withProperty("leo.attachment.storage.s3.presign-upload-ttl", "12m")
                .withProperty("leo.attachment.storage.s3.presign-preview-ttl", "8m");

        AttachmentProperties properties = Binder.get(environment)
                .bind("leo.attachment", Bindable.of(AttachmentProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(32));
        assertThat(properties.getStorage().getType()).isEqualTo("s3");
        assertThat(properties.getStorage().getKeyPrefix()).isEqualTo("uploads");
        assertThat(properties.getStorage().getLocal().getPath()).isEqualTo("/data/uploads");

        AttachmentProperties.S3 s3 = properties.getStorage().getS3();
        assertThat(s3.getEndpoint()).isEqualTo("https://s3.example.test");
        assertThat(s3.getRegion()).isEqualTo("ap-guangzhou");
        assertThat(s3.getBucket()).isEqualTo("erp-attachments");
        assertThat(s3.getAccessKey()).isEqualTo("access-key");
        assertThat(s3.getSecretKey()).isEqualTo("secret-key");
        assertThat(s3.isPathStyleAccess()).isFalse();
        assertThat(s3.getConnectTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(s3.getReadTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(s3.getPresignUploadTtl()).isEqualTo(Duration.ofMinutes(12));
        assertThat(s3.getPresignPreviewTtl()).isEqualTo(Duration.ofMinutes(8));
    }
}
