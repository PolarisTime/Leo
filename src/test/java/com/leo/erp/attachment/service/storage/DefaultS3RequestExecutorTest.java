package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultS3RequestExecutorTest {

    @Test
    void shouldExecuteRequestAndReturnBytes() throws Exception {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        HttpServer server = startServer("/bytes", 201, body);
        try {
            DefaultS3RequestExecutor executor = new DefaultS3RequestExecutor(new AttachmentProperties());

            S3RequestExecutor.S3Response response = executor.execute(request(server, "/bytes"));

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body()).isEqualTo(body);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExecuteRequestAndReturnStream() throws Exception {
        byte[] body = "stream".getBytes(StandardCharsets.UTF_8);
        HttpServer server = startServer("/stream", 202, body);
        try {
            DefaultS3RequestExecutor executor = new DefaultS3RequestExecutor(new AttachmentProperties());

            S3RequestExecutor.S3StreamResponse response = executor.executeForStream(request(server, "/stream"));

            assertThat(response.statusCode()).isEqualTo(202);
            assertThat(response.body().readAllBytes()).isEqualTo(body);
        } finally {
            server.stop(0);
        }
    }

    private HttpRequest request(HttpServer server, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
                .GET()
                .build();
    }

    private HttpServer startServer(String path, int status, byte[] body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(status, body.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        server.start();
        return server;
    }
}
