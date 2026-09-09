package com.leo.erp.attachment.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.service.AttachmentDirectUploadTokenService.DirectUploadTokenPayload;
import com.leo.erp.attachment.service.storage.DirectUploadAttachmentStorage;
import com.leo.erp.attachment.service.storage.AttachmentStorageResolver;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttachmentUploadService 极端情况测试：空文件、超限、非法扩展名、非法来源/所有者/摘要、
 * 落库失败清理存储与清理自身失败的异常保留语义。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentUploadServiceTest {

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private AttachmentFilenameResolver filenameResolver;

    @Mock
    private AttachmentStorageResolver storageResolver;

    @Mock
    private AttachmentMetadataService metadataService;

    @Mock
    private AttachmentDirectUploadTokenService directUploadTokenService;

    private AttachmentUploadService service;

    @BeforeEach
    void setUp() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setMaxFileSize(DataSize.ofBytes(100));
        service = new AttachmentUploadService(
                idGenerator,
                properties,
                filenameResolver,
                storageResolver,
                metadataService,
                directUploadTokenService,
                new AttachmentResponseAssembler()
        );
    }

    private MockMultipartFile file(String originalFilename, byte[] content) {
        return new MockMultipartFile("file", originalFilename, "text/plain", content);
    }

    @Test
    void upload_shouldRejectNullFile() throws IOException {
        assertThatThrownBy(() -> service.upload(null, null, "m", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
        verifyNoStorageInteractions();
    }

    @Test
    void upload_shouldRejectEmptyFile() throws IOException {
        assertThatThrownBy(() -> service.upload(file("a.txt", new byte[0]), null, "m", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件不能为空");
        verifyNoStorageInteractions();
    }

    @Test
    void upload_shouldRejectOversizedFile() throws IOException {
        assertThatThrownBy(() -> service.upload(file("a.txt", new byte[101]), null, "m", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件超过大小限制");
        verifyNoStorageInteractions();
    }

    @Test
    void upload_shouldRejectBlockedExtension() throws IOException {
        assertThatThrownBy(() -> service.upload(file("evil.EXE", new byte[10]), null, "m", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型: .exe");
        verifyNoStorageInteractions();
    }

    @Test
    void upload_shouldRejectInvalidOwnerUserId() throws IOException {
        assertThatThrownBy(() -> service.upload(file("a.txt", new byte[1]), null, "m", 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件所有者无效");
        assertThatThrownBy(() -> service.upload(file("a.txt", new byte[1]), null, "m", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件所有者无效");
        verifyNoStorageInteractions();
    }

    @Test
    void upload_shouldRejectUnknownSourceType() throws IOException {
        assertThatThrownBy(() -> service.upload(file("a.txt", new byte[1]), "EMAIL", "m", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的上传来源");
        verifyNoStorageInteractions();
    }

    @Test
    void upload_shouldStoreBeforePersistingMetadata() throws IOException {
        when(idGenerator.nextId()).thenReturn(7L);
        when(filenameResolver.buildStoredFileName(eq(7L), eq("a.txt"), any()))
                .thenReturn("stored-7.txt");
        when(storageResolver.store(anyString(), any())).thenReturn("/local/2026/09/7/stored-7.txt");
        when(metadataService.saveUploadedFileMetadata(
                eq(7L), eq(5L), eq("stored-7.txt"), eq("a.txt"), any(), eq(4L), eq("PAGE_UPLOAD"), any()))
                .thenAnswer(invocation -> {
                    AttachmentFile entity = new AttachmentFile();
                    entity.setId(7L);
                    entity.setAccessKey("access-key-7");
                    entity.setOriginalFileName("a.txt");
                    entity.setFileName("stored-7.txt");
                    return entity;
                });

        AttachmentView view = service.upload(file("a.txt", new byte[4]), "page_upload", "m", 5L);

        InOrder order = inOrder(storageResolver, metadataService);
        order.verify(storageResolver).store(anyString(), any());
        order.verify(metadataService).saveUploadedFileMetadata(
                eq(7L), eq(5L), eq("stored-7.txt"), eq("a.txt"), any(), eq(4L), eq("PAGE_UPLOAD"), any());
        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.name()).isEqualTo("a.txt");
        assertThat(view.previewType()).isEqualTo("none");
    }

    @Test
    void upload_shouldCleanupStoredFileAndRethrowOriginalWhenMetadataFails() throws IOException {
        when(idGenerator.nextId()).thenReturn(7L);
        when(filenameResolver.buildStoredFileName(anyLong(), anyString(), any()))
                .thenReturn("stored-7.txt");
        when(storageResolver.store(anyString(), any())).thenReturn("/local/stored-7.txt");
        IllegalStateException failure = new IllegalStateException("db down");
        when(metadataService.saveUploadedFileMetadata(anyLong(), any(), anyString(), anyString(), any(), anyLong(), anyString(), anyString()))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.upload(file("a.txt", new byte[4]), null, "m", 5L))
                .isSameAs(failure);

        verify(storageResolver).delete("/local/stored-7.txt");
    }

    @Test
    void upload_shouldKeepOriginalErrorWhenCleanupAlsoFails() throws IOException {
        when(idGenerator.nextId()).thenReturn(7L);
        when(filenameResolver.buildStoredFileName(anyLong(), anyString(), any()))
                .thenReturn("stored-7.txt");
        when(storageResolver.store(anyString(), any())).thenReturn("/local/stored-7.txt");
        IllegalStateException failure = new IllegalStateException("db down");
        when(metadataService.saveUploadedFileMetadata(anyLong(), any(), anyString(), anyString(), any(), anyLong(), anyString(), anyString()))
                .thenThrow(failure);
        doThrow(new IOException("delete broken")).when(storageResolver).delete(anyString());

        assertThatThrownBy(() -> service.upload(file("a.txt", new byte[4]), null, "m", 5L))
                .isSameAs(failure);
    }

    @Test
    void prepareDirectUpload_shouldRejectInvalidSha256() throws IOException {
        assertThatThrownBy(() -> service.prepareDirectUpload(
                "a.txt", "text/plain", 10L, null, "m", "not-hash", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件校验值无效");
        verifyNoStorageInteractions();
    }

    @Test
    void prepareDirectUpload_shouldRejectInvalidSourceAndOwner() throws IOException {
        assertThatThrownBy(() -> service.prepareDirectUpload(
                "a.txt", "text/plain", 10L, "BAD", "m", sha256(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的上传来源");
        assertThatThrownBy(() -> service.prepareDirectUpload(
                "a.txt", "text/plain", 10L, null, "m", sha256(), -1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件所有者无效");
        verifyNoStorageInteractions();
    }

    @Test
    void prepareDirectUpload_shouldIssueTokenWithNormalizedValues() {
        when(idGenerator.nextId()).thenReturn(9L);
        when(filenameResolver.buildStoredFileName(eq(9L), anyString(), eq("text/plain")))
                .thenReturn("stored-9.txt");
        DirectUploadAttachmentStorage.PresignedUpload presigned =
                new DirectUploadAttachmentStorage.PresignedUpload(
                        URI.create("https://s3/upload"), "PUT", Map.of("x", "1"), "/s3/stored-9.txt", Instant.EPOCH);
        when(storageResolver.prepareDirectUpload(anyString(), eq("text/plain"), eq(10L), eq(sha256())))
                .thenReturn(presigned);
        when(directUploadTokenService.issue(any(DirectUploadTokenPayload.class))).thenReturn("token-1");

        AttachmentService.DirectUploadPrepareResult result = service.prepareDirectUpload(
                " A.txt ", "text/plain", 10L, "clipboard_paste", "  m  ", sha256(), 3L);

        assertThat(result.attachmentId()).isEqualTo(9L);
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.storagePath()).isEqualTo("/s3/stored-9.txt");
        assertThat(result.uploadUrl()).isEqualTo(URI.create("https://s3/upload"));
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(result.headers()).containsEntry("x", "1");
        assertThat(result.expiresAt()).isEqualTo(Instant.EPOCH);

        org.mockito.ArgumentCaptor<DirectUploadTokenPayload> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(DirectUploadTokenPayload.class);
        verify(directUploadTokenService).issue(payloadCaptor.capture());
        DirectUploadTokenPayload payload = payloadCaptor.getValue();
        assertThat(payload.sourceType()).isEqualTo("CLIPBOARD_PASTE");
        assertThat(payload.moduleKey()).isEqualTo("m");
        assertThat(payload.sha256Hex()).isEqualTo(sha256());
        assertThat(payload.ownerUserId()).isEqualTo(3L);
    }

    @Test
    void completeDirectUpload_shouldCleanupAndRethrowWhenMetadataFails() throws IOException {
        DirectUploadTokenPayload payload = new DirectUploadTokenPayload(
                9L, "object-key", "/s3/stored-9.txt", "stored-9.txt", "a.txt",
                "text/plain", 10L, "PAGE_UPLOAD", "m", 3L, sha256(), Instant.EPOCH.getEpochSecond());
        when(directUploadTokenService.verify(eq("token-1"), eq(9L), eq("m"), eq(3L))).thenReturn(payload);
        IllegalStateException failure = new IllegalStateException("db down");
        when(metadataService.saveUploadedFileMetadata(
                eq(9L), eq(3L), eq("stored-9.txt"), eq("a.txt"), eq("text/plain"),
                eq(10L), eq("PAGE_UPLOAD"), eq("/s3/stored-9.txt")))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.completeDirectUpload(9L, "token-1", "m", 3L))
                .isSameAs(failure);

        org.mockito.InOrder order = inOrder(storageResolver, metadataService);
        order.verify(storageResolver).verifyDirectUpload("/s3/stored-9.txt", 10L, sha256());
        order.verify(metadataService).saveUploadedFileMetadata(
                eq(9L), eq(3L), eq("stored-9.txt"), eq("a.txt"), eq("text/plain"),
                eq(10L), eq("PAGE_UPLOAD"), eq("/s3/stored-9.txt"));
        verify(storageResolver).delete("/s3/stored-9.txt");
    }

    @Test
    void completeDirectUpload_shouldReturnViewAfterSuccessfulPersist() throws IOException {
        DirectUploadTokenPayload payload = new DirectUploadTokenPayload(
                9L, "object-key", "/s3/stored-9.txt", "stored-9.pdf", "a.pdf",
                "application/pdf", 10L, "PAGE_UPLOAD", "m", 3L, sha256(), Instant.EPOCH.getEpochSecond());
        when(directUploadTokenService.verify(eq("token-1"), eq(9L), eq("m"), eq(3L))).thenReturn(payload);
        AttachmentFile saved = new AttachmentFile();
        saved.setId(9L);
        saved.setAccessKey("access-key-9");
        saved.setOriginalFileName("a.pdf");
        saved.setFileName("stored-9.pdf");
        saved.setFileExtension("pdf");
        when(metadataService.saveUploadedFileMetadata(
                eq(9L), eq(3L), eq("stored-9.pdf"), eq("a.pdf"), eq("application/pdf"),
                eq(10L), eq("PAGE_UPLOAD"), eq("/s3/stored-9.txt")))
                .thenReturn(saved);

        AttachmentView view = service.completeDirectUpload(9L, "token-1", "m", 3L);

        assertThat(view.id()).isEqualTo(9L);
        assertThat(view.previewType()).isEqualTo("pdf");
        verify(storageResolver, never()).delete(anyString());
    }

    private static String sha256() {
        return "a".repeat(64);
    }

    private void verifyNoStorageInteractions() throws IOException {
        verify(storageResolver, never()).store(anyString(), any());
        verify(metadataService, never()).saveUploadedFileMetadata(
                anyLong(), any(), anyString(), anyString(), any(), anyLong(), anyString(), anyString());
    }
}
