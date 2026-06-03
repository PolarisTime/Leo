package com.leo.erp.attachment.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentFileTest {

    @Test
    void shouldCreateAttachmentFileWithAllFields() {
        AttachmentFile file = new AttachmentFile();
        file.setId(1L);
        file.setFileName("file.pdf");
        file.setOriginalFileName("original.pdf");
        file.setFileExtension("pdf");
        file.setContentType("application/pdf");
        file.setFileSize(1024L);
        file.setStoragePath("local:path/to/file");
        file.setAccessKey("access-key");
        file.setSourceType("PAGE_UPLOAD");

        assertThat(file.getId()).isEqualTo(1L);
        assertThat(file.getFileName()).isEqualTo("file.pdf");
        assertThat(file.getOriginalFileName()).isEqualTo("original.pdf");
        assertThat(file.getFileExtension()).isEqualTo("pdf");
        assertThat(file.getContentType()).isEqualTo("application/pdf");
        assertThat(file.getFileSize()).isEqualTo(1024L);
        assertThat(file.getStoragePath()).isEqualTo("local:path/to/file");
        assertThat(file.getAccessKey()).isEqualTo("access-key");
        assertThat(file.getSourceType()).isEqualTo("PAGE_UPLOAD");
    }

    @Test
    void shouldHandleNullValues() {
        AttachmentFile file = new AttachmentFile();
        file.setId(null);
        file.setFileName(null);
        file.setOriginalFileName(null);
        file.setFileExtension(null);
        file.setContentType(null);
        file.setFileSize(null);
        file.setStoragePath(null);
        file.setAccessKey(null);
        file.setSourceType(null);

        assertThat(file.getId()).isNull();
        assertThat(file.getFileName()).isNull();
        assertThat(file.getOriginalFileName()).isNull();
        assertThat(file.getFileExtension()).isNull();
        assertThat(file.getContentType()).isNull();
        assertThat(file.getFileSize()).isNull();
        assertThat(file.getStoragePath()).isNull();
        assertThat(file.getAccessKey()).isNull();
        assertThat(file.getSourceType()).isNull();
    }
}