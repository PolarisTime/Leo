package com.leo.erp.attachment.web.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentBindingResponseTest {

    @Test
    void shouldCreateResponseWithAllFields() {
        AttachmentResponse attachment = new AttachmentResponse(
                1L,
                "name",
                "file.pdf",
                "original.pdf",
                "application/pdf",
                1024L,
                "PAGE_UPLOAD",
                "uploader",
                null,
                true,
                "pdf",
                "/preview",
                "/download"
        );

        AttachmentBindingResponse response = new AttachmentBindingResponse(
                "module-key",
                100L,
                List.of(attachment)
        );

        assertThat(response.moduleKey()).isEqualTo("module-key");
        assertThat(response.recordId()).isEqualTo(100L);
        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().get(0)).isEqualTo(attachment);
    }

    @Test
    void shouldHandleNullValues() {
        AttachmentBindingResponse response = new AttachmentBindingResponse(
                null,
                null,
                null
        );

        assertThat(response.moduleKey()).isNull();
        assertThat(response.recordId()).isNull();
        assertThat(response.attachments()).isNull();
    }
}