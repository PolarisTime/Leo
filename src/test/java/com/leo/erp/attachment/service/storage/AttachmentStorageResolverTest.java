package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;

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
}