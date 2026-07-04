package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class S3SignerTest {

    @Test
    void shouldSignRequestWithCorrectHeaders() {
        S3ChecksumUtil checksumUtil = mock(S3ChecksumUtil.class);
        S3PathParser pathParser = mock(S3PathParser.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:30:45Z"), ZoneOffset.UTC);

        S3Signer signer = new S3Signer(checksumUtil, pathParser, clock);

        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint("http://localhost:9000");
        s3.setBucket("test-bucket");
        s3.setRegion("us-east-1");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setPathStyleAccess(true);

        when(pathParser.buildUri("test-key", s3)).thenReturn(java.net.URI.create("http://localhost:9000/test-bucket/test-key"));
        when(pathParser.hostHeader(any())).thenReturn("localhost:9000");
        when(checksumUtil.hexSha256(anyString())).thenReturn("checksum-hash");
        when(checksumUtil.toHex(any())).thenReturn("signature");

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString("test body");
        HttpRequest request = signer.signedRequest("PUT", "test-key", "payload-hash", "text/plain", s3, bodyPublisher);

        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.headers().firstValue("Authorization")).isPresent();
        assertThat(request.headers().firstValue("x-amz-date")).hasValue("20260424T123045Z");
        assertThat(request.headers().firstValue("x-amz-content-sha256")).hasValue("payload-hash");
        assertThat(request.headers().firstValue("Content-Type")).hasValue("text/plain");

        verify(pathParser).buildUri("test-key", s3);
        verify(checksumUtil).hexSha256(anyString());
    }

    @Test
    void shouldSignGetRequestWithoutContentType() {
        S3ChecksumUtil checksumUtil = mock(S3ChecksumUtil.class);
        S3PathParser pathParser = mock(S3PathParser.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:30:45Z"), ZoneOffset.UTC);

        S3Signer signer = new S3Signer(checksumUtil, pathParser, clock);

        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint("http://localhost:9000");
        s3.setBucket("test-bucket");
        s3.setRegion("us-east-1");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setPathStyleAccess(true);

        when(pathParser.buildUri("test-key", s3)).thenReturn(java.net.URI.create("http://localhost:9000/test-bucket/test-key"));
        when(pathParser.hostHeader(any())).thenReturn("localhost:9000");
        when(checksumUtil.hexSha256(anyString())).thenReturn("checksum-hash");
        when(checksumUtil.toHex(any())).thenReturn("signature");

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
        HttpRequest request = signer.signedRequest("GET", "test-key", "payload-hash", null, s3, bodyPublisher);

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.headers().firstValue("Content-Type")).isEmpty();
    }

    @Test
    void shouldThrowExceptionForUnsupportedMethod() {
        S3ChecksumUtil checksumUtil = mock(S3ChecksumUtil.class);
        S3PathParser pathParser = mock(S3PathParser.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:30:45Z"), ZoneOffset.UTC);

        S3Signer signer = new S3Signer(checksumUtil, pathParser, clock);

        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint("http://localhost:9000");
        s3.setBucket("test-bucket");
        s3.setRegion("us-east-1");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setPathStyleAccess(true);

        when(pathParser.buildUri("test-key", s3)).thenReturn(java.net.URI.create("http://localhost:9000/test-bucket/test-key"));
        when(pathParser.hostHeader(any())).thenReturn("localhost:9000");

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();

        try {
            signer.signedRequest("POST", "test-key", "payload-hash", null, s3, bodyPublisher);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("不支持的 S3 请求方法");
        }
    }

    @Test
    void shouldSignDeleteRequestWithBlankContentTypeAndDefaultReadTimeout() {
        S3ChecksumUtil checksumUtil = mock(S3ChecksumUtil.class);
        S3PathParser pathParser = mock(S3PathParser.class);
        S3Signer signer = new S3Signer(checksumUtil, pathParser);
        AttachmentProperties.S3 s3 = s3Properties();
        s3.setReadTimeout(null);
        URI uri = URI.create("http://localhost:9000");

        when(pathParser.buildUri("test-key", s3)).thenReturn(uri);
        when(pathParser.hostHeader(uri)).thenReturn("localhost:9000");
        when(checksumUtil.hexSha256(anyString())).thenReturn("checksum-hash");
        when(checksumUtil.toHex(any())).thenReturn("signature");

        HttpRequest request = signer.signedRequest(
                "DELETE",
                "test-key",
                "payload-hash",
                "   ",
                s3,
                HttpRequest.BodyPublishers.noBody()
        );

        assertThat(request.method()).isEqualTo("DELETE");
        assertThat(request.timeout()).hasValue(Duration.ofSeconds(30));
        assertThat(request.headers().firstValue("Content-Type")).isEmpty();

        ArgumentCaptor<String> canonicalRequest = ArgumentCaptor.forClass(String.class);
        verify(checksumUtil).hexSha256(canonicalRequest.capture());
        assertThat(canonicalRequest.getValue()).startsWith("DELETE\n/\n\n");
    }

    @Test
    void shouldCanonicalizeNullRawPathAsRoot() {
        S3ChecksumUtil checksumUtil = mock(S3ChecksumUtil.class);
        S3PathParser pathParser = mock(S3PathParser.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:30:45Z"), ZoneOffset.UTC);
        S3Signer signer = new S3Signer(checksumUtil, pathParser, clock);
        AttachmentProperties.S3 s3 = s3Properties();
        URI uri = URI.create("http:test-key");

        when(pathParser.buildUri("test-key", s3)).thenReturn(uri);
        when(pathParser.hostHeader(uri)).thenReturn("localhost:9000");
        when(checksumUtil.hexSha256(anyString())).thenReturn("checksum-hash");
        when(checksumUtil.toHex(any())).thenReturn("signature");

        assertThatThrownBy(() -> signer.signedRequest(
                "POST",
                "test-key",
                "payload-hash",
                null,
                s3,
                HttpRequest.BodyPublishers.noBody()
        )).isInstanceOf(IllegalArgumentException.class);

        ArgumentCaptor<String> canonicalRequest = ArgumentCaptor.forClass(String.class);
        verify(checksumUtil).hexSha256(canonicalRequest.capture());
        assertThat(canonicalRequest.getValue()).startsWith("POST\n/\n\n");
    }

    @Test
    void shouldWrapHmacSecurityException() {
        S3Signer signer = new S3Signer(mock(S3ChecksumUtil.class), mock(S3PathParser.class));

        try (MockedStatic<Mac> mac = mockStatic(Mac.class)) {
            mac.when(() -> Mac.getInstance("HmacSHA256"))
                    .thenThrow(new NoSuchAlgorithmException("missing algorithm"));

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    signer,
                    "hmacSha256",
                    new byte[]{1},
                    "data"
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("HmacSHA256 计算失败")
                    .hasCauseInstanceOf(NoSuchAlgorithmException.class);
        }
    }

    private static AttachmentProperties.S3 s3Properties() {
        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint("http://localhost:9000");
        s3.setBucket("test-bucket");
        s3.setRegion("us-east-1");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setPathStyleAccess(true);
        return s3;
    }
}
