package com.leo.erp.attachment.service.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class S3ChecksumUtilTest {

    private final S3ChecksumUtil checksumUtil = new S3ChecksumUtil();

    @Test
    void shouldCalculateSha256ForByteArray() {
        String input = "hello world";
        String hash = checksumUtil.hexSha256(input.getBytes(StandardCharsets.UTF_8));
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void shouldCalculateSha256ForInputStream() throws IOException {
        String input = "hello world";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        String hash = checksumUtil.hexSha256(inputStream);
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void shouldIgnoreZeroLengthReadsWhenHashingStream() throws IOException {
        InputStream inputStream = new InputStream() {
            private final byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
            private int offset;
            private boolean returnedZero;

            @Override
            public int read() {
                if (offset >= bytes.length) {
                    return -1;
                }
                return bytes[offset++];
            }

            @Override
            public int read(byte[] buffer, int off, int len) {
                if (!returnedZero) {
                    returnedZero = true;
                    return 0;
                }
                if (offset >= bytes.length) {
                    return -1;
                }
                int count = Math.min(len, bytes.length - offset);
                System.arraycopy(bytes, offset, buffer, off, count);
                offset += count;
                return count;
            }
        };

        String hash = checksumUtil.hexSha256(inputStream);

        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void shouldCalculateSha256ForString() {
        String hash = checksumUtil.hexSha256("hello world");
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void shouldReturnEmptyBodyHash() {
        String hash = checksumUtil.emptyBodyHash();
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void shouldConvertBytesToHex() {
        byte[] data = {0x01, 0x02, 0x03, 0x0a, 0x0f};
        String hex = checksumUtil.toHex(data);
        assertThat(hex).isEqualTo("0102030a0f");
    }

    @Test
    void shouldWrapMissingSha256AlgorithmForByteArray() {
        try (var messageDigest = mockStatic(MessageDigest.class)) {
            messageDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> checksumUtil.hexSha256("hello".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256 不可用")
                    .hasCauseInstanceOf(NoSuchAlgorithmException.class);
        }
    }

    @Test
    void shouldWrapMissingSha256AlgorithmForInputStream() {
        try (var messageDigest = mockStatic(MessageDigest.class)) {
            messageDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> checksumUtil.hexSha256(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256 不可用")
                    .hasCauseInstanceOf(NoSuchAlgorithmException.class);
        }
    }
}
