package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AttachmentResponseAssembler 单元测试：预览类型识别、存储类型解析、URL 装配与可空字段兜底。
 */
class AttachmentResponseAssemblerTest {

    private final AttachmentResponseAssembler assembler = new AttachmentResponseAssembler();

    private AttachmentFile entity(String fileExtension, String storagePath) {
        AttachmentFile entity = new AttachmentFile();
        entity.setId(123L);
        entity.setOriginalFileName("报告.pdf");
        entity.setFileName("stored-123.pdf");
        entity.setFileExtension(fileExtension);
        entity.setContentType("application/pdf");
        entity.setFileSize(2048L);
        entity.setStoragePath(storagePath);
        entity.setAccessKey("access-key-1");
        entity.setSourceType("PAGE_UPLOAD");
        entity.setCreatedName("张三");
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        return entity;
    }

    @Test
    void toResponse_shouldAssembleAllFieldsWithDownloadUrl() {
        AttachmentView view = assembler.toResponse(entity("pdf", "/local/2026/01/a.pdf"), null);

        assertThat(view.id()).isEqualTo(123L);
        assertThat(view.name()).isEqualTo("报告.pdf");
        assertThat(view.fileName()).isEqualTo("stored-123.pdf");
        assertThat(view.originalFileName()).isEqualTo("报告.pdf");
        assertThat(view.contentType()).isEqualTo("application/pdf");
        assertThat(view.fileSize()).isEqualTo(2048L);
        assertThat(view.sourceType()).isEqualTo("PAGE_UPLOAD");
        assertThat(view.uploader()).isEqualTo("张三");
        assertThat(view.uploadTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(view.previewSupported()).isTrue();
        assertThat(view.previewType()).isEqualTo("pdf");
        assertThat(view.previewUrl())
                .isEqualTo("/api/v2.0/attachments/123/preview?accessKey=access-key-1");
        assertThat(view.downloadUrl())
                .isEqualTo("/api/v2.0/attachments/123/download?accessKey=access-key-1");
        assertThat(view.storageType()).isEqualTo("local");
        assertThat(view.storageLabel()).isEqualTo("本机存储");
    }

    @Test
    void toResponse_shouldAppendEncodedModuleQueryWhenModuleKeyPresent() {
        AttachmentView view = assembler.toResponse(entity("pdf", "/a.pdf"), "sales order");

        assertThat(view.downloadUrl())
                .isEqualTo("/api/v2.0/attachments/123/download?accessKey=access-key-1&moduleKey=sales+order");
    }

    @Test
    void toResponse_shouldNullPreviewUrlWhenPreviewNotSupported() {
        AttachmentView view = assembler.toResponse(entity("xlsx", "/a.xlsx"), null);

        assertThat(view.previewType()).isEqualTo("none");
        assertThat(view.previewSupported()).isFalse();
        assertThat(view.previewUrl()).isNull();
        assertThat(view.downloadUrl()).isNotNull();
    }

    @Test
    void toResponse_shouldHandleNullOptionalFields() {
        AttachmentFile entity = new AttachmentFile();
        entity.setId(1L);
        entity.setAccessKey("access-key-1");
        entity.setStoragePath(null);

        AttachmentView view = assembler.toResponse(entity, null);

        assertThat(view.name()).isNull();
        assertThat(view.fileSize()).isNull();
        assertThat(view.uploader()).isEqualTo("system");
        assertThat(view.uploadTime()).isNull();
        assertThat(view.previewType()).isEqualTo("none");
        assertThat(view.storageType()).isEqualTo("local");
        assertThat(view.downloadUrl()).isEqualTo("/api/v2.0/attachments/1/download?accessKey=access-key-1");
    }

    @Test
    void detectPreviewType_shouldCoverAllBranches() {
        assertThat(assembler.detectPreviewType(entity("pdf", null))).isEqualTo("pdf");
        assertThat(assembler.detectPreviewType(entity("PNG", null))).isEqualTo("image");
        assertThat(assembler.detectPreviewType(entity("  Jpg ", null))).isEqualTo("image");
        assertThat(assembler.detectPreviewType(entity("webp", null))).isEqualTo("image");
        assertThat(assembler.detectPreviewType(entity("xlsx", null))).isEqualTo("none");
        assertThat(assembler.detectPreviewType(entity(null, null))).isEqualTo("none");
        assertThat(assembler.detectPreviewType(entity("", null))).isEqualTo("none");
    }

    @Test
    void resolveResponseContentType_shouldMapByPreviewTypeAndExtension() {
        assertThat(assembler.resolveResponseContentType(entity("pdf", null), "pdf"))
                .isEqualTo("application/pdf");
        assertThat(assembler.resolveResponseContentType(entity("png", null), "image"))
                .isEqualTo("image/png");
        assertThat(assembler.resolveResponseContentType(entity("jpeg", null), "image"))
                .isEqualTo("image/jpeg");
        assertThat(assembler.resolveResponseContentType(entity("gif", null), "image"))
                .isEqualTo("image/gif");
        assertThat(assembler.resolveResponseContentType(entity("webp", null), "image"))
                .isEqualTo("image/webp");
        assertThat(assembler.resolveResponseContentType(entity("bmp", null), "image"))
                .isEqualTo("image/bmp");
        assertThat(assembler.resolveResponseContentType(entity("tif", null), "image"))
                .isEqualTo("application/octet-stream");
        assertThat(assembler.resolveResponseContentType(entity("pdf", null), "none"))
                .isEqualTo("application/octet-stream");
        assertThat(assembler.resolveResponseContentType(entity("pdf", null), "unknown"))
                .isEqualTo("application/octet-stream");
    }

    @Test
    void toPresentation_shouldResolveStorageTypeAndLabel() {
        assertThat(assembler.toPresentation(entity("pdf", "s3://bucket/key"), null).storageType()).isEqualTo("s3");
        assertThat(assembler.toPresentation(entity("pdf", "s3://bucket/key"), null).storageLabel()).isEqualTo("S3存储");
        assertThat(assembler.toPresentation(entity("pdf", "S3://bucket/key"), null).storageType()).isEqualTo("s3");
        assertThat(assembler.toPresentation(entity("pdf", "s3x://bucket"), null).storageType()).isEqualTo("local");
        assertThat(assembler.toPresentation(entity("pdf", "/local/a.pdf"), null).storageType()).isEqualTo("local");
        assertThat(assembler.toPresentation(entity("pdf", ":weird"), null).storageType()).isEqualTo("local");
        assertThat(assembler.toPresentation(entity("pdf", "  "), null).storageType()).isEqualTo("local");
        assertThat(assembler.toPresentation(entity("pdf", null), null).storageType()).isEqualTo("local");
    }
}
