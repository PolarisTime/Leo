package com.leo.erp.attachment.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;

public interface S3RequestExecutor {

    S3Response execute(HttpRequest request) throws IOException, InterruptedException;

    S3StreamResponse executeForStream(HttpRequest request) throws IOException, InterruptedException;

    record S3Response(int statusCode, byte[] body) {
    }

    record S3StreamResponse(int statusCode, InputStream body) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            body.close();
        }
    }
}
