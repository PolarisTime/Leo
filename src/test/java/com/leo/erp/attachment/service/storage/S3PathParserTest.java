package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3PathParserTest {

    private final S3PathParser parser = new S3PathParser();

    @Test
    void shouldParseValidStoragePath() {
        S3PathParser.ParsedStoragePath result = parser.parseStoragePath("s3:my-bucket/path/to/file.pdf");
        assertThat(result.bucket()).isEqualTo("my-bucket");
        assertThat(result.objectKey()).isEqualTo("path/to/file.pdf");
    }

    @Test
    void shouldThrowExceptionForInvalidStoragePath() {
        assertThatThrownBy(() -> parser.parseStoragePath("invalid-path"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 附件路径格式错误");
    }

    @Test
    void shouldThrowExceptionForMissingSlash() {
        assertThatThrownBy(() -> parser.parseStoragePath("s3:bucket"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 附件路径格式错误");
    }

    @Test
    void shouldBuildStoragePath() {
        String path = parser.buildStoragePath("my-bucket", "path/to/file.pdf");
        assertThat(path).isEqualTo("s3:my-bucket/path/to/file.pdf");
    }

    @Test
    void shouldBuildUriWithPathStyleAccess() {
        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint("http://localhost:9000");
        s3.setBucket("test-bucket");
        s3.setPathStyleAccess(true);

        URI uri = parser.buildUri("test-key", s3);
        assertThat(uri.toString()).isEqualTo("http://localhost:9000/test-bucket/test-key");
    }

    @Test
    void shouldBuildUriWithVirtualHostedStyle() {
        AttachmentProperties.S3 s3 = new AttachmentProperties.S3();
        s3.setEndpoint("http://s3.amazonaws.com");
        s3.setBucket("test-bucket");
        s3.setPathStyleAccess(false);

        URI uri = parser.buildUri("test-key", s3);
        assertThat(uri.toString()).isEqualTo("http://test-bucket.s3.amazonaws.com/test-key");
    }

    @Test
    void shouldEncodeObjectKey() {
        String encoded = parser.encodeObjectKey("path/to/file with spaces.pdf");
        assertThat(encoded).isEqualTo("path/to/file%20with%20spaces.pdf");
    }

    @Test
    void shouldReturnHostHeaderWithoutPort() {
        URI uri = URI.create("http://localhost/test");
        String host = parser.hostHeader(uri);
        assertThat(host).isEqualTo("localhost");
    }

    @Test
    void shouldReturnHostHeaderWithPort() {
        URI uri = URI.create("http://localhost:9000/test");
        String host = parser.hostHeader(uri);
        assertThat(host).isEqualTo("localhost:9000");
    }

    @Test
    void shouldReturnHostHeaderWithDefaultHttpPort() {
        URI uri = URI.create("http://localhost:80/test");
        String host = parser.hostHeader(uri);
        assertThat(host).isEqualTo("localhost");
    }

    @Test
    void shouldReturnHostHeaderWithDefaultHttpsPort() {
        URI uri = URI.create("https://localhost:443/test");
        String host = parser.hostHeader(uri);
        assertThat(host).isEqualTo("localhost");
    }

    @Test
    void shouldNormalizeEndpointPath() {
        assertThat(parser.normalizedEndpointPath(null)).isEqualTo("");
        assertThat(parser.normalizedEndpointPath("")).isEqualTo("");
        assertThat(parser.normalizedEndpointPath("/")).isEqualTo("");
        assertThat(parser.normalizedEndpointPath("/prefix")).isEqualTo("/prefix");
        assertThat(parser.normalizedEndpointPath("/prefix/")).isEqualTo("/prefix");
    }

    @Test
    void shouldParseEndpoint() {
        URI uri = parser.parseEndpoint("http://localhost:9000");
        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPort()).isEqualTo(9000);
    }

    @Test
    void shouldThrowExceptionForInvalidEndpoint() {
        assertThatThrownBy(() -> parser.parseEndpoint("invalid uri"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S3 Endpoint 配置错误");
    }

    @Test
    void shouldIdentifyBlankValues() {
        assertThat(parser.isBlank(null)).isTrue();
        assertThat(parser.isBlank("")).isTrue();
        assertThat(parser.isBlank("  ")).isTrue();
        assertThat(parser.isBlank("value")).isFalse();
    }
}