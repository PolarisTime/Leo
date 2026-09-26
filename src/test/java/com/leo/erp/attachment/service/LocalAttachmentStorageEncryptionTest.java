package com.leo.erp.attachment.service;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.attachment.service.storage.AttachmentContentCryptor;
import com.leo.erp.system.securitykey.service.SecurityKeyService;
import com.leo.erp.attachment.service.storage.LocalAttachmentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回归: 本地存储的加密开关必须取 {@code local.encrypted-storage}, 不得误用 S3 配置。
 */
class LocalAttachmentStorageEncryptionTest {

    @TempDir
    Path tempDir;

    @Test
    void localEncryptionFollowsLocalConfigNotS3() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().getLocal().setPath(tempDir.toString());
        // 仅开启 S3 加密, 本地显式关闭: 本地文件必须保持明文。
        properties.getStorage().getS3().setEncryptedStorage(true);
        properties.getStorage().getLocal().setEncryptedStorage(false);

        LocalAttachmentStorage storage = new LocalAttachmentStorage(properties, null);
        storage.storeBytes("plain.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");

        Path stored = tempDir.resolve("plain.txt");
        assertThat(Files.readString(stored)).isEqualTo("hello");
    }

    @Test
    void localEncryptionEnabledWhenLocalConfigOn() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().getLocal().setPath(tempDir.toString());
        properties.getStorage().getS3().setEncryptedStorage(false);
        properties.getStorage().getLocal().setEncryptedStorage(true);

        SecurityKeyService keyService = mock(SecurityKeyService.class);
        when(keyService.getActiveDataMaterial()).thenReturn(
                new SecurityKeyService.ResolvedSecretMaterial(
                        "config", 1, "0123456789abcdef0123456789abcdef", null, null, "fp"));
        LocalAttachmentStorage storage =
                new LocalAttachmentStorage(properties, new AttachmentContentCryptor(keyService));
        storage.storeBytes("secret.bin", "secret".getBytes(StandardCharsets.UTF_8), "application/octet-stream");

        byte[] stored = Files.readAllBytes(tempDir.resolve("secret.bin"));
        assertThat(new String(stored, StandardCharsets.US_ASCII)).startsWith("LEOENC1");
    }
}
