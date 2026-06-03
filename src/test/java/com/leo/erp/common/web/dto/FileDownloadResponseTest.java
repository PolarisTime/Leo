package com.leo.erp.common.web.dto;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class FileDownloadResponseTest {

    @Test
    void recordAccessors() {
        byte[] content = "hello".getBytes();
        FileDownloadResponse response = new FileDownloadResponse(
                "test.txt", MediaType.TEXT_PLAIN, content
        );

        assertThat(response.filename()).isEqualTo("test.txt");
        assertThat(response.contentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.content()).isEqualTo(content);
    }

    @Test
    void recordEquality() {
        byte[] content = {1, 2, 3};
        FileDownloadResponse a = new FileDownloadResponse("f.xlsx", MediaType.APPLICATION_OCTET_STREAM, content);
        FileDownloadResponse b = new FileDownloadResponse("f.xlsx", MediaType.APPLICATION_OCTET_STREAM, content);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        FileDownloadResponse response = new FileDownloadResponse(
                "data.csv", MediaType.TEXT_PLAIN, new byte[0]
        );
        assertThat(response.toString()).contains("data.csv");
    }
}
