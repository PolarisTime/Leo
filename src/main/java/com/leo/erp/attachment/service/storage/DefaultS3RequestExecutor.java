package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class DefaultS3RequestExecutor implements S3RequestExecutor {

    private final HttpClient httpClient;

    public DefaultS3RequestExecutor(AttachmentProperties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getStorage().getS3().getConnectTimeout())
                .build();
    }

    @Override
    public S3Response execute(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return new S3Response(response.statusCode(), response.body());
    }

    @Override
    public S3StreamResponse executeForStream(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return new S3StreamResponse(response.statusCode(), response.body());
    }
}
