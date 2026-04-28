package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class S3CompatibleAttachmentStorageTest {

    private static S3ChecksumUtil checksumUtil = new S3ChecksumUtil();
    private static S3PathParser pathParser = new S3PathParser();

    private static S3CompatibleAttachmentStorage createStorage(AttachmentProperties properties, S3RequestExecutor executor, Clock clock) {
        S3Signer signer = new S3Signer(checksumUtil, pathParser, clock);
        return new S3CompatibleAttachmentStorage(properties, executor, checksumUtil, pathParser, signer);
    }

    @Test
    void shouldBuildSignedPutRequestForS3CompatibleEndpoint() throws Exception {
        AttachmentProperties properties = s3Properties();
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        S3RequestExecutor executor = new S3RequestExecutor() {
            @Override
            public S3Response execute(HttpRequest request) {
                captured.set(request);
                return new S3Response(200, new byte[0]);
            }

            @Override
            public S3StreamResponse executeForStream(HttpRequest request) {
                captured.set(request);
                return new S3StreamResponse(200, new ByteArrayInputStream(new byte[0]));
            }
        };

        S3CompatibleAttachmentStorage storage = createStorage(properties, executor,
                Clock.fixed(Instant.parse("2026-04-24T12:30:45Z"), ZoneOffset.UTC));

        String storagePath = storage.store(
                "attachments/2026/04/1/test.pdf",
                new MockMultipartFile("file", "test.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8))
        );

        HttpRequest request = captured.get();
        assertThat(storagePath).isEqualTo("s3:test-bucket/attachments/2026/04/1/test.pdf");
        assertThat(request.uri().toString()).isEqualTo("http://127.0.0.1:9000/test-bucket/attachments/2026/04/1/test.pdf");
        assertThat(request.headers().firstValue("Authorization")).hasValueSatisfying(value -> assertThat(value).startsWith("AWS4-HMAC-SHA256 "));
        assertThat(request.headers().firstValue("x-amz-date")).hasValue("20260424T123045Z");
        assertThat(request.headers().firstValue("Content-Type")).hasValue("application/pdf");
    }

    @Test
    void shouldBuildSignedGetRequestForS3Download() throws Exception {
        AttachmentProperties properties = s3Properties();
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        S3RequestExecutor executor = new S3RequestExecutor() {
            @Override
            public S3Response execute(HttpRequest request) {
                captured.set(request);
                return new S3Response(200, "payload".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public S3StreamResponse executeForStream(HttpRequest request) {
                captured.set(request);
                return new S3StreamResponse(200, new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)));
            }
        };

        S3CompatibleAttachmentStorage storage = createStorage(properties, executor,
                Clock.fixed(Instant.parse("2026-04-24T12:30:45Z"), ZoneOffset.UTC));

        String content = new String(
                storage.load("s3:test-bucket/attachments/2026/04/1/test.pdf").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(content).isEqualTo("payload");
        assertThat(captured.get().uri().toString()).isEqualTo("http://127.0.0.1:9000/test-bucket/attachments/2026/04/1/test.pdf");
        assertThat(captured.get().method()).isEqualTo("GET");
    }

    private AttachmentProperties s3Properties() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().setType("s3");
        properties.getStorage().getS3().setEndpoint("http://127.0.0.1:9000");
        properties.getStorage().getS3().setBucket("test-bucket");
        properties.getStorage().getS3().setRegion("us-east-1");
        properties.getStorage().getS3().setAccessKey("minio");
        properties.getStorage().getS3().setSecretKey("miniosecret");
        properties.getStorage().getS3().setPathStyleAccess(true);
        return properties;
    }
}
