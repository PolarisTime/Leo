package com.leo.erp.attachment.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentServiceExtendedTest {

    @Test
    void shouldRejectUploadWhenPageUploadDisabled() {
        UploadRuleService uploadRuleService = mock(UploadRuleService.class);
        when(uploadRuleService.isPageUploadEnabled("sales-order")).thenReturn(false);

        AttachmentService service = new AttachmentService(
                mock(AttachmentFileRepository.class),
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                uploadRuleService,
                mock(AttachmentStorageResolver.class),
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> service.upload(file, "PAGE_UPLOAD", "sales-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用附件标志");
    }

    @Test
    void shouldRejectBlockedFileExtensions() {
        UploadRuleService uploadRuleService = mock(UploadRuleService.class);
        when(uploadRuleService.isPageUploadEnabled(any())).thenReturn(true);
        when(uploadRuleService.buildPageUploadFileName(any(), any(), any())).thenReturn("test.jsp");

        AttachmentService service = new AttachmentService(
                mock(AttachmentFileRepository.class),
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                uploadRuleService,
                mock(AttachmentStorageResolver.class),
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        MultipartFile file = new MockMultipartFile("file", "malware.jsp", "application/octet-stream", "data".getBytes());

        assertThatThrownBy(() -> service.upload(file, "PAGE_UPLOAD", "sales-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void shouldRejectBlockedExeExtension() {
        UploadRuleService uploadRuleService = mock(UploadRuleService.class);
        when(uploadRuleService.isPageUploadEnabled(any())).thenReturn(true);

        AttachmentService service = new AttachmentService(
                mock(AttachmentFileRepository.class),
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                uploadRuleService,
                mock(AttachmentStorageResolver.class),
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        MultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "data".getBytes());

        assertThatThrownBy(() -> service.upload(file, "PAGE_UPLOAD"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void shouldReturnEmptyListWhenIdsNull() {
        AttachmentService service = new AttachmentService(
                mock(AttachmentFileRepository.class),
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                mock(UploadRuleService.class),
                mock(AttachmentStorageResolver.class),
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        List<AttachmentView> result = service.getAttachments(null, "sales-order");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipMissingIdsInGetAttachments() {
        AttachmentFileRepository repository = mock(AttachmentFileRepository.class);
        AttachmentFile file = new AttachmentFile();
        file.setId(1L);
        file.setOriginalFileName("test.pdf");
        file.setFileName("test.pdf");
        file.setFileExtension("pdf");
        file.setContentType("application/pdf");
        file.setFileSize(100L);
        file.setStoragePath("local:1/test.pdf");
        file.setSourceType("PAGE_UPLOAD");
        file.setAccessKey("key123");
        when(repository.findAllByIdInAndDeletedFlagFalse(List.of(1L, 2L))).thenReturn(List.of(file));

        UploadRuleService uploadRuleService = mock(UploadRuleService.class);
        when(uploadRuleService.isPageUploadEnabled(any())).thenReturn(true);

        AttachmentService service = new AttachmentService(
                repository,
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                uploadRuleService,
                mock(AttachmentStorageResolver.class),
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        List<AttachmentView> result = service.getAttachments(List.of(1L, 2L), "sales-order");

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldSkipValidationWhenIdsEmpty() {
        AttachmentService service = new AttachmentService(
                mock(AttachmentFileRepository.class),
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                mock(UploadRuleService.class),
                mock(AttachmentStorageResolver.class),
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        service.validateAttachmentIds(List.of());
    }

    @Test
    void shouldThrowWhenStorageLoadFails() throws IOException {
        AttachmentFileRepository repository = mock(AttachmentFileRepository.class);
        AttachmentFile file = new AttachmentFile();
        file.setId(1L);
        file.setOriginalFileName("test.pdf");
        file.setContentType("application/pdf");
        file.setFileSize(100L);
        file.setStoragePath("local:1/test.pdf");
        file.setAccessKey("key123");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(file));

        AttachmentStorageResolver storageResolver = mock(AttachmentStorageResolver.class);
        when(storageResolver.load("local:1/test.pdf")).thenThrow(new IOException("disk error"));

        AttachmentService service = new AttachmentService(
                repository,
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                mock(UploadRuleService.class),
                storageResolver,
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        assertThatThrownBy(() -> service.loadForDownload(1L, "key123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件读取失败");
    }

    @Test
    void shouldRejectPreviewWhenNotSupported() throws IOException {
        AttachmentFileRepository repository = mock(AttachmentFileRepository.class);
        AttachmentFile file = new AttachmentFile();
        file.setId(1L);
        file.setOriginalFileName("test.dat");
        file.setContentType("application/octet-stream");
        file.setFileSize(100L);
        file.setStoragePath("local:1/test.dat");
        file.setAccessKey("key123");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(file));

        AttachmentStorageResolver storageResolver = mock(AttachmentStorageResolver.class);
        when(storageResolver.load("local:1/test.dat")).thenReturn(new org.springframework.core.io.ByteArrayResource("data".getBytes()));

        AttachmentService service = new AttachmentService(
                repository,
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                mock(UploadRuleService.class),
                storageResolver,
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        assertThatThrownBy(() -> service.loadForPreview(1L, "key123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持预览");
    }

    @Test
    void shouldSetAttachmentDispositionWhenNotInline() throws IOException {
        AttachmentFileRepository repository = mock(AttachmentFileRepository.class);
        AttachmentFile file = new AttachmentFile();
        file.setId(1L);
        file.setOriginalFileName("test.pdf");
        file.setContentType("application/pdf");
        file.setFileSize(100L);
        file.setStoragePath("local:1/test.pdf");
        file.setAccessKey("key123");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(file));

        AttachmentStorageResolver storageResolver = mock(AttachmentStorageResolver.class);
        when(storageResolver.load("local:1/test.pdf")).thenReturn(new org.springframework.core.io.ByteArrayResource("data".getBytes()));

        UploadRuleService uploadRuleService = mock(UploadRuleService.class);
        when(uploadRuleService.isPageUploadEnabled(any())).thenReturn(true);

        AttachmentService service = new AttachmentService(
                repository,
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                uploadRuleService,
                storageResolver,
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        AttachmentDownloadResource resource = service.loadDownloadResource(1L, "key123", false);

        assertThat(resource.contentDisposition()).contains("attachment");
    }

    @Test
    void shouldFallbackToOctetStreamWhenContentTypeBlank() throws IOException {
        AttachmentFileRepository repository = mock(AttachmentFileRepository.class);
        AttachmentFile file = new AttachmentFile();
        file.setId(1L);
        file.setOriginalFileName("test.dat");
        file.setContentType("");
        file.setFileSize(100L);
        file.setStoragePath("local:1/test.dat");
        file.setAccessKey("key123");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(java.util.Optional.of(file));

        AttachmentStorageResolver storageResolver = mock(AttachmentStorageResolver.class);
        when(storageResolver.load("local:1/test.dat")).thenReturn(new org.springframework.core.io.ByteArrayResource("data".getBytes()));

        UploadRuleService uploadRuleService = mock(UploadRuleService.class);
        when(uploadRuleService.isPageUploadEnabled(any())).thenReturn(true);

        AttachmentService service = new AttachmentService(
                repository,
                mock(SnowflakeIdGenerator.class),
                properties(),
                mock(AttachmentFilenameResolver.class),
                uploadRuleService,
                storageResolver,
                mock(ImageWatermarkService.class),
                mock(PdfWatermarkService.class)
        );

        AttachmentDownloadResource resource = service.loadDownloadResource(1L, "key123", false);

        assertThat(resource.contentType().toString()).isEqualTo("application/octet-stream");
    }

    private AttachmentProperties properties() {
        AttachmentProperties props = new AttachmentProperties();
        props.setMaxFileSize(org.springframework.util.unit.DataSize.ofMegabytes(10));
        return props;
    }
}
