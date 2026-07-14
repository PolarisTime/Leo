package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.oss.service.OssSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AttachmentStorageResolverTest {

    @Test
    void shouldDelegateStoreToConfiguredStorage() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        when(localStorage.store("test-key", null)).thenReturn("local:test-key");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("local");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        String result = resolver.store("test-key", null);
        assertThat(result).isEqualTo("local:test-key");
        verify(localStorage).store("test-key", null);
    }

    @Test
    void shouldDelegateStoreBytesToConfiguredStorage() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        byte[] content = "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(localStorage.storeBytes("bytes-key", content, "text/plain")).thenReturn("local:bytes-key");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("local");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        String result = resolver.storeBytes("bytes-key", content, "text/plain");

        assertThat(result).isEqualTo("local:bytes-key");
        verify(localStorage).storeBytes("bytes-key", content, "text/plain");
    }

    @Test
    void shouldUseRuntimeOssSettingStorageTypeWhenAvailable() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        AttachmentStorage s3Storage = mock(AttachmentStorage.class);
        when(s3Storage.type()).thenReturn("s3");
        when(s3Storage.store("runtime-key", null)).thenReturn("s3:bucket/runtime-key");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("local");
        OssSettingService ossSettingService = mock(OssSettingService.class);
        when(ossSettingService.resolveRuntimeSetting()).thenReturn(new OssSettingService.ResolvedOssSetting(
                "s3",
                "attachments",
                "/tmp/uploads",
                properties.getStorage().getS3(),
                false,
                false
        ));

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(
                List.of(localStorage, s3Storage),
                properties,
                ossSettingService
        );

        String result = resolver.store("runtime-key", null);

        assertThat(result).isEqualTo("s3:bucket/runtime-key");
        verify(s3Storage).store("runtime-key", null);
        verify(localStorage, never()).store("runtime-key", null);
    }

    @Test
    void shouldDelegateLoadToResolvedStorage() throws IOException {
        AttachmentStorage s3Storage = mock(AttachmentStorage.class);
        when(s3Storage.type()).thenReturn("s3");
        Resource resource = mock(Resource.class);
        when(s3Storage.load("s3:bucket/key")).thenReturn(resource);

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("s3");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(s3Storage), properties);

        Resource result = resolver.load("s3:bucket/key");
        assertThat(result).isEqualTo(resource);
        verify(s3Storage).load("s3:bucket/key");
    }

    @Test
    void shouldDelegateDeleteToResolvedStorage() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("local");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        resolver.delete("local:path/to/file");
        verify(localStorage).delete("local:path/to/file");
    }

    @Test
    void shouldRejectDirectUploadWhenConfiguredStorageDoesNotSupportIt() {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("local");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        assertThatThrownBy(() -> resolver.prepareDirectUpload("key", "text/plain", 1L, "checksum"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前附件存储不支持直传");
        assertThatThrownBy(() -> resolver.verifyDirectUpload("local:path", 1L, "checksum"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前附件存储不支持直传");
    }

    @Test
    void shouldDelegateDirectUploadOperationsToDirectStorage() {
        DirectUploadAttachmentStorage directStorage = mock(DirectUploadAttachmentStorage.class);
        when(directStorage.type()).thenReturn("s3");
        DirectUploadAttachmentStorage.PresignedUpload upload = new DirectUploadAttachmentStorage.PresignedUpload(
                URI.create("https://upload.example.com/test.txt"),
                "PUT",
                Map.of(),
                "s3:bucket/test.txt",
                Instant.parse("2026-07-01T08:00:00Z")
        );
        when(directStorage.prepareDirectUpload("test.txt", "text/plain", 5L, "checksum")).thenReturn(upload);

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("s3");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(directStorage), properties);

        assertThat(resolver.prepareDirectUpload("test.txt", "text/plain", 5L, "checksum")).isEqualTo(upload);
        resolver.verifyDirectUpload("s3:bucket/test.txt", 5L, "checksum");
        URI accessUrl = URI.create("https://download.example.com/test.txt");
        when(directStorage.createPresignedAccessUrl("s3:bucket/test.txt", "test.txt", "text/plain", true))
                .thenReturn(accessUrl);

        assertThat(resolver.createPresignedAccessUrl("s3:bucket/test.txt", "test.txt", "text/plain", true))
                .isEqualTo(accessUrl);
        verify(directStorage).verifyDirectUpload("s3:bucket/test.txt", 5L, "checksum");
    }

    @Test
    void shouldThrowExceptionWhenNoStorageConfigured() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("unknown");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(), properties);

        assertThatThrownBy(() -> resolver.store("key", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置可用的附件存储后端");
    }

    @Test
    void shouldFallbackToLocalWhenStoragePathTypeNotFound() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        Resource resource = mock(Resource.class);
        when(localStorage.load("unknown:path")).thenReturn(resource);

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("local");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        Resource result = resolver.load("unknown:path");
        assertThat(result).isEqualTo(resource);
        verify(localStorage).load("unknown:path");
    }

    @Test
    void shouldFallbackToLocalWhenStoragePathIsNullOrHasNoTypePrefix() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        Resource nullPathResource = mock(Resource.class);
        Resource plainPathResource = mock(Resource.class);
        when(localStorage.load(null)).thenReturn(nullPathResource);
        when(localStorage.load("path/without/type")).thenReturn(plainPathResource);

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("local");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        assertThat(resolver.load(null)).isEqualTo(nullPathResource);
        assertThat(resolver.load("path/without/type")).isEqualTo(plainPathResource);
        verify(localStorage).load(null);
        verify(localStorage).load("path/without/type");
    }

    @Test
    void shouldThrowWhenStoragePathCannotResolveAndLocalStorageIsMissing() {
        AttachmentStorage s3Storage = mock(AttachmentStorage.class);
        when(s3Storage.type()).thenReturn("s3");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(s3Storage), new AttachmentProperties());

        assertThatThrownBy(() -> resolver.load(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法解析附件存储后端");
        assertThatThrownBy(() -> resolver.load("path/without/type"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法解析附件存储后端");
        assertThatThrownBy(() -> resolver.load("unknown:path"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法解析附件存储后端");
    }

    @Test
    void shouldNormalizeStorageType() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        when(localStorage.store("key", null)).thenReturn("local:key");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("LOCAL");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        String result = resolver.store("key", null);
        assertThat(result).isEqualTo("local:key");
    }

    @Test
    void shouldDefaultBlankConfiguredStorageTypeToLocal() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        when(localStorage.store("key", null)).thenReturn("local:key");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("   ");

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        assertThat(resolver.store("key", null)).isEqualTo("local:key");
    }

    @Test
    void shouldDefaultNullConfiguredStorageTypeToLocal() throws IOException {
        AttachmentStorage localStorage = mock(AttachmentStorage.class);
        when(localStorage.type()).thenReturn("local");
        when(localStorage.store("key", null)).thenReturn("local:key");

        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType(null);

        AttachmentStorageResolver resolver = new AttachmentStorageResolver(List.of(localStorage), properties);

        assertThat(resolver.store("key", null)).isEqualTo("local:key");
    }
}
