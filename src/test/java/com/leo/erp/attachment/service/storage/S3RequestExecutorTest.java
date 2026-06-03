package com.leo.erp.attachment.service.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class S3RequestExecutorTest {

    @Test
    void shouldCreateS3Response() {
        byte[] body = "test body".getBytes();
        S3RequestExecutor.S3Response response = new S3RequestExecutor.S3Response(200, body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(body);
    }

    @Test
    void shouldCreateS3StreamResponse() {
        InputStream inputStream = new ByteArrayInputStream("test stream".getBytes());
        S3RequestExecutor.S3StreamResponse response = new S3RequestExecutor.S3StreamResponse(200, inputStream);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(inputStream);
    }

    @Test
    void shouldCloseStreamResponse() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test stream".getBytes());
        S3RequestExecutor.S3StreamResponse response = new S3RequestExecutor.S3StreamResponse(200, inputStream);

        response.close();
        // No exception should be thrown
    }
}