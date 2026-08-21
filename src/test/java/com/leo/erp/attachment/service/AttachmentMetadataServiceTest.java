package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttachmentMetadataService 极端情况测试：落库字段映射、fileSize 边界、可空字段、accessKey 契约。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentMetadataServiceTest {

    @Mock
    private AttachmentFileRepository repository;

    @Mock
    private AttachmentFilenameResolver filenameResolver;

    @InjectMocks
    private AttachmentMetadataService service;

    @BeforeEach
    void stubResolver() {
        // 所有用例都会触发解析（service 每次落库前必调），统一返回 report.xlsx。
        when(filenameResolver.parseFilenameParts(any(), any()))
                .thenReturn(new AttachmentFilenameResolver.FilenameParts("report", "xlsx"));
    }

    @Test
    void saveUploadedFileMetadata_shouldPersistEntityWithResolvedExtension() {
        when(repository.save(any(AttachmentFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttachmentFile returned = service.saveUploadedFileMetadata(
                1L, 100L, "stored-report.xlsx", "报告.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                2048L, "UPLOAD", "/uploads/2026/08/stored-report.xlsx"
        );

        ArgumentCaptor<AttachmentFile> captor = ArgumentCaptor.forClass(AttachmentFile.class);
        verify(repository).save(captor.capture());
        AttachmentFile saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getOwnerUserId()).isEqualTo(100L);
        assertThat(saved.getFileName()).isEqualTo("stored-report.xlsx");
        assertThat(saved.getOriginalFileName()).isEqualTo("报告.xlsx");
        assertThat(saved.getFileExtension()).isEqualTo("xlsx");
        assertThat(saved.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(saved.getFileSize()).isEqualTo(2048L);
        assertThat(saved.getStoragePath()).isEqualTo("/uploads/2026/08/stored-report.xlsx");
        assertThat(saved.getSourceType()).isEqualTo("UPLOAD");
        // 返回值即落库实体（仓库 save 返回入参）。
        assertThat(returned).isSameAs(saved);
    }

    @Test
    void saveUploadedFileMetadata_shouldPersistWithBoundaryFileSize() {
        when(repository.save(any(AttachmentFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveUploadedFileMetadata(1L, null, "a.pdf", "a.pdf", "application/pdf", 0L, "UPLOAD", "/a.pdf");
        service.saveUploadedFileMetadata(2L, null, "b.pdf", "b.pdf", "application/pdf", Long.MAX_VALUE, "UPLOAD", "/b.pdf");

        ArgumentCaptor<AttachmentFile> captor = ArgumentCaptor.forClass(AttachmentFile.class);
        verify(repository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues()).extracting(AttachmentFile::getFileSize)
                .containsExactly(0L, Long.MAX_VALUE);
    }

    @Test
    void saveUploadedFileMetadata_shouldHandleNullOwnerAndOptionalFields() {
        when(repository.save(any(AttachmentFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttachmentFile returned = service.saveUploadedFileMetadata(
                1L, null, "clipboard", null, null, 100L, "PASTE", null
        );

        ArgumentCaptor<AttachmentFile> captor = ArgumentCaptor.forClass(AttachmentFile.class);
        verify(repository).save(captor.capture());
        AttachmentFile saved = captor.getValue();

        assertThat(saved.getOwnerUserId()).isNull();
        assertThat(saved.getContentType()).isNull();
        assertThat(saved.getStoragePath()).isNull();
        assertThat(returned).isSameAs(saved);
    }

    @Test
    void saveUploadedFileMetadata_shouldGenerateHexAccessKey() {
        when(repository.save(any(AttachmentFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveUploadedFileMetadata(1L, 100L, "a.xlsx", "a.xlsx", "xlsx-type", 10L, "UPLOAD", "/a.xlsx");

        ArgumentCaptor<AttachmentFile> captor = ArgumentCaptor.forClass(AttachmentFile.class);
        verify(repository).save(captor.capture());
        AttachmentFile saved = captor.getValue();

        // 两段 UUID 去横线拼接：64 位、全小写十六进制。
        assertThat(saved.getAccessKey()).matches("[0-9a-f]{64}");
    }

    @Test
    void saveUploadedFileMetadata_shouldReturnSavedEntity() {
        AttachmentFile persisted = new AttachmentFile();
        when(repository.save(any(AttachmentFile.class))).thenReturn(persisted);

        AttachmentFile returned = service.saveUploadedFileMetadata(
                1L, 100L, "a.pdf", "a.pdf", "application/pdf", 10L, "UPLOAD", "/a.pdf"
        );

        assertThat(returned).isSameAs(persisted);
    }

    // 防御缺口：storedFileName 为 null 时由 resolver 兜底（service 直接透传），
    // service 本身不校验。此处验证 null 文件名不抛 NPE 且正常落库。
    @Test
    void saveUploadedFileMetadata_shouldHandleNullStoredFileName() {
        when(repository.save(any(AttachmentFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttachmentFile returned = service.saveUploadedFileMetadata(
                1L, 100L, null, null, "application/pdf", 10L, "UPLOAD", null
        );

        ArgumentCaptor<AttachmentFile> captor = ArgumentCaptor.forClass(AttachmentFile.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFileName()).isNull();
        assertThat(returned).isSameAs(captor.getValue());
    }
}
