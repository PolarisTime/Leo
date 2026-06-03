package com.leo.erp.attachment.service.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

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
}