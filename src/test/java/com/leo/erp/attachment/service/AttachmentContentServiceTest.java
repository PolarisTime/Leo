package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AttachmentContentService 极端情况测试：IO 失败、预览不支持、Content-Type 兜底与预签名 URL 判空。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentContentServiceTest {

    @Mock
    private AttachmentQueryService queryService;

    @Mock
    private AttachmentStorageResolver storageResolver;

    private AttachmentContentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentContentService(
                queryService, new AttachmentResponseAssembler(), storageResolver);
    }

    private AttachmentFile entity(String fileExtension, String contentType) {
        AttachmentFile entity = new AttachmentFile();
        entity.setId(1L);
        entity.setFileName("stored-1." + fileExtension);
        entity.setOriginalFileName("origin." + fileExtension);
        entity.setFileExtension(fileExtension);
        entity.setContentType(contentType);
        entity.setStoragePath("/local/stored-1." + fileExtension);
        entity.setAccessKey("stored-key");
        return entity;
    }

    @Test
    void loadForDownload_shouldWrapIoFailureAsInternalError() throws IOException {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("pdf", "application/pdf"));
        when(storageResolver.load("/local/stored-1.pdf")).thenThrow(new IOException("disk broken"));

        assertThatThrownBy(() -> service.loadForDownload(1L, "stored-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件读取失败");
    }

    @Test
    void loadForDownload_shouldReturnPayloadForPdf() throws IOException {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("pdf", "application/pdf"));
        org.springframework.core.io.Resource resource = mock(org.springframework.core.io.Resource.class);
        when(storageResolver.load("/local/stored-1.pdf")).thenReturn(resource);

        AttachmentService.AttachmentDownloadPayload payload = service.loadForDownload(1L, "stored-key");

        assertThat(payload.fileName()).isEqualTo("stored-1.pdf");
        assertThat(payload.contentType()).isEqualTo("application/pdf");
        assertThat(payload.resource()).isSameAs(resource);
        assertThat(payload.previewSupported()).isTrue();
        assertThat(payload.previewType()).isEqualTo("pdf");
    }

    @Test
    void loadForPreview_shouldRejectUnsupportedType() throws IOException {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("xlsx", null));
        when(storageResolver.load("/local/stored-1.xlsx")).thenReturn(mock(org.springframework.core.io.Resource.class));

        assertThatThrownBy(() -> service.loadForPreview(1L, "stored-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前附件不支持预览");
    }

    @Test
    void loadForPreview_shouldReturnPayloadWhenSupported() throws IOException {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("png", "image/png"));
        when(storageResolver.load("/local/stored-1.png")).thenReturn(mock(org.springframework.core.io.Resource.class));

        AttachmentService.AttachmentDownloadPayload payload = service.loadForPreview(1L, "stored-key");

        assertThat(payload.previewType()).isEqualTo("image");
        assertThat(payload.contentType()).isEqualTo("image/png");
    }

    @Test
    void loadDownloadResource_shouldFallbackToOctetStreamWhenContentTypeBlank() throws IOException {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("xlsx", " "));
        when(storageResolver.load("/local/stored-1.xlsx")).thenReturn(mock(org.springframework.core.io.Resource.class));

        AttachmentDownloadResource result = service.loadDownloadResource(1L, "stored-key", false);

        assertThat(result.contentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(result.contentDisposition())
                .startsWith("attachment;").contains("filename*=UTF-8''stored-1.xlsx");
    }

    @Test
    void loadDownloadResource_shouldParseKnownContentTypeAndUseInlineDisposition() throws IOException {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("pdf", "application/pdf"));
        when(storageResolver.load("/local/stored-1.pdf")).thenReturn(mock(org.springframework.core.io.Resource.class));

        AttachmentDownloadResource result = service.loadDownloadResource(1L, "stored-key", true);

        assertThat(result.contentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(result.contentDisposition()).startsWith("inline;");
    }

    @Test
    void createPresignedAccessUrl_shouldRejectInlineWhenPreviewUnsupported() {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("xlsx", null));

        assertThatThrownBy(() -> service.createPresignedAccessUrl(1L, "stored-key", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前附件不支持预览");
    }

    @Test
    void createPresignedAccessUrl_shouldReturnNullWhenResolverReturnsNull() {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("pdf", "application/pdf"));
        when(storageResolver.createPresignedAccessUrl(
                "/local/stored-1.pdf", "stored-1.pdf", "application/pdf", false)).thenReturn(null);

        assertThat(service.createPresignedAccessUrl(1L, "stored-key", false)).isNull();
    }

    @Test
    void createPresignedAccessUrl_shouldReturnUrlWithInlineFlag() {
        when(queryService.getAttachment(1L, "stored-key")).thenReturn(entity("pdf", "application/pdf"));
        URI url = URI.create("https://s3.example.com/presigned");
        when(storageResolver.createPresignedAccessUrl(
                "/local/stored-1.pdf", "stored-1.pdf", "application/pdf", true)).thenReturn(url);

        AttachmentService.PresignedAttachmentUrl result = service.createPresignedAccessUrl(1L, "stored-key", true);

        assertThat(result).isNotNull();
        assertThat(result.url()).isSameAs(url);
        assertThat(result.inline()).isTrue();
    }

    @Test
    void createPresignedAccessUrl_shouldPropagateNotFoundFromQueryService() {
        when(queryService.getAttachment(404L, "stored-key"))
                .thenThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.NOT_FOUND, "附件不存在"));

        assertThatThrownBy(() -> service.createPresignedAccessUrl(404L, "stored-key", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");
    }
}
