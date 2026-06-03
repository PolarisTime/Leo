package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentViewTest {

    @Test
    void shouldCreateViewWithAllFields() {
        LocalDateTime uploadTime = LocalDateTime.of(2026, 4, 24, 12, 30, 45);
        AttachmentView view = new AttachmentView(
                1L,
                "name",
                "file.pdf",
                "original.pdf",
                "application/pdf",
                1024L,
                "PAGE_UPLOAD",
                "uploader",
                uploadTime,
                true,
                "pdf",
                "/preview",
                "/download"
        );

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.name()).isEqualTo("name");
        assertThat(view.fileName()).isEqualTo("file.pdf");
        assertThat(view.originalFileName()).isEqualTo("original.pdf");
        assertThat(view.contentType()).isEqualTo("application/pdf");
        assertThat(view.fileSize()).isEqualTo(1024L);
        assertThat(view.sourceType()).isEqualTo("PAGE_UPLOAD");
        assertThat(view.uploader()).isEqualTo("uploader");
        assertThat(view.uploadTime()).isEqualTo(uploadTime);
        assertThat(view.previewSupported()).isTrue();
        assertThat(view.previewType()).isEqualTo("pdf");
        assertThat(view.previewUrl()).isEqualTo("/preview");
        assertThat(view.downloadUrl()).isEqualTo("/download");
    }

    @Test
    void shouldHandleNullValues() {
        AttachmentView view = new AttachmentView(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(view.id()).isNull();
        assertThat(view.name()).isNull();
        assertThat(view.fileName()).isNull();
        assertThat(view.originalFileName()).isNull();
        assertThat(view.contentType()).isNull();
        assertThat(view.fileSize()).isNull();
        assertThat(view.sourceType()).isNull();
        assertThat(view.uploader()).isNull();
        assertThat(view.uploadTime()).isNull();
        assertThat(view.previewSupported()).isNull();
        assertThat(view.previewType()).isNull();
        assertThat(view.previewUrl()).isNull();
        assertThat(view.downloadUrl()).isNull();
    }
}