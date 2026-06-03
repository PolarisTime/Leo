package com.leo.erp.attachment.web.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentResponseTest {

    @Test
    void shouldCreateResponseWithAllFields() {
        LocalDateTime uploadTime = LocalDateTime.of(2026, 4, 24, 12, 30, 45);
        AttachmentResponse response = new AttachmentResponse(
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

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("name");
        assertThat(response.fileName()).isEqualTo("file.pdf");
        assertThat(response.originalFileName()).isEqualTo("original.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.fileSize()).isEqualTo(1024L);
        assertThat(response.sourceType()).isEqualTo("PAGE_UPLOAD");
        assertThat(response.uploader()).isEqualTo("uploader");
        assertThat(response.uploadTime()).isEqualTo(uploadTime);
        assertThat(response.previewSupported()).isTrue();
        assertThat(response.previewType()).isEqualTo("pdf");
        assertThat(response.previewUrl()).isEqualTo("/preview");
        assertThat(response.downloadUrl()).isEqualTo("/download");
    }

    @Test
    void shouldHandleNullValues() {
        AttachmentResponse response = new AttachmentResponse(
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

        assertThat(response.id()).isNull();
        assertThat(response.name()).isNull();
        assertThat(response.fileName()).isNull();
        assertThat(response.originalFileName()).isNull();
        assertThat(response.contentType()).isNull();
        assertThat(response.fileSize()).isNull();
        assertThat(response.sourceType()).isNull();
        assertThat(response.uploader()).isNull();
        assertThat(response.uploadTime()).isNull();
        assertThat(response.previewSupported()).isNull();
        assertThat(response.previewType()).isNull();
        assertThat(response.previewUrl()).isNull();
        assertThat(response.downloadUrl()).isNull();
    }
}