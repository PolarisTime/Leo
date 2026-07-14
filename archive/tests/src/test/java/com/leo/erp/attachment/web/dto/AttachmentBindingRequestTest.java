package com.leo.erp.attachment.web.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentBindingRequestTest {

    @Test
    void shouldCreateRequestWithAllFields() {
        AttachmentBindingRequest request = new AttachmentBindingRequest(
                "module-key",
                100L,
                List.of(1L, 2L, 3L)
        );

        assertThat(request.moduleKey()).isEqualTo("module-key");
        assertThat(request.recordId()).isEqualTo(100L);
        assertThat(request.attachmentIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldHandleNullValues() {
        AttachmentBindingRequest request = new AttachmentBindingRequest(
                null,
                null,
                null
        );

        assertThat(request.moduleKey()).isNull();
        assertThat(request.recordId()).isNull();
        assertThat(request.attachmentIds()).isNull();
    }
}