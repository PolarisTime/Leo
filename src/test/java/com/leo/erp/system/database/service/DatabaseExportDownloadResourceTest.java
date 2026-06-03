package com.leo.erp.system.database.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseExportDownloadResourceTest {

    @Test
    void shouldCreateRecord() {
        var resource = new ByteArrayResource("test".getBytes());
        var contentType = MediaType.APPLICATION_OCTET_STREAM;
        long contentLength = 4L;
        String contentDisposition = "attachment; filename=\"export.sql\"";

        var downloadResource = new DatabaseExportDownloadResource(
                resource, contentType, contentLength, contentDisposition
        );

        assertThat(downloadResource.resource()).isEqualTo(resource);
        assertThat(downloadResource.contentType()).isEqualTo(contentType);
        assertThat(downloadResource.contentLength()).isEqualTo(contentLength);
        assertThat(downloadResource.contentDisposition()).isEqualTo(contentDisposition);
    }
}
