package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AttachmentDownloadResourceTest {

    @Test
    void shouldCreateResourceWithAllFields() {
        Resource resource = mock(Resource.class);
        MediaType contentType = MediaType.APPLICATION_PDF;
        String contentDisposition = "attachment; filename=\"file.pdf\"";

        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                resource,
                contentType,
                contentDisposition
        );

        assertThat(downloadResource.resource()).isEqualTo(resource);
        assertThat(downloadResource.contentType()).isEqualTo(contentType);
        assertThat(downloadResource.contentDisposition()).isEqualTo(contentDisposition);
    }

    @Test
    void shouldHandleNullValues() {
        AttachmentDownloadResource downloadResource = new AttachmentDownloadResource(
                null,
                null,
                null
        );

        assertThat(downloadResource.resource()).isNull();
        assertThat(downloadResource.contentType()).isNull();
        assertThat(downloadResource.contentDisposition()).isNull();
    }
}