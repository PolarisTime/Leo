package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.oss.service.OssSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalAttachmentStorageExtendedTest {

    @Test
    void shouldRejectPathTraversalOnStore() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());

        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> storage.store("../../etc/passwd", file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法文件路径");
    }

    @Test
    void shouldThrowWhenLocalFileNotFound() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());

        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        assertThatThrownBy(() -> storage.load("local:999/nonexistent.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件文件不存在");
    }

    @Test
    void shouldRejectPathTraversalOnLoad() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());

        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        assertThatThrownBy(() -> storage.load("local:../../etc/passwd"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldSilentlySkipWhenDeletingNonExistentFile() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());

        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        storage.delete("local:999/nonexistent.pdf");
    }

    @Test
    void shouldReturnStoragePathWithLocalPrefix() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());

        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        String path = storage.store("1/test.pdf", file);

        assertThat(path).startsWith("local:");
        assertThat(path).contains("test.pdf");
    }

    @Test
    void shouldLoadUsingLegacyPathFormat() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());

        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        Path file = root.resolve("legacy-test.pdf");
        Files.write(file, "data".getBytes());

        var resource = storage.load(file.toString());

        assertThat(resource.exists()).isTrue();
    }

    @Test
    void shouldStoreBytesUsingRuntimeOssSettingPath() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        OssSettingService ossSettingService = mock(OssSettingService.class);
        when(ossSettingService.resolveRuntimeSetting()).thenReturn(runtimeSetting(root, false));
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props, ossSettingService);

        String storagePath = storage.storeBytes(
                "runtime/bytes.txt",
                "runtime-content".getBytes(StandardCharsets.UTF_8),
                "text/plain"
        );

        assertThat(storagePath).isEqualTo("local:runtime/bytes.txt");
        assertThat(Files.readString(root.resolve("runtime/bytes.txt"))).isEqualTo("runtime-content");
    }

    @Test
    void shouldStoreEncryptedContentWhenRuntimeSettingRequiresEncryption() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        OssSettingService ossSettingService = mock(OssSettingService.class);
        AttachmentContentCryptor cryptor = mock(AttachmentContentCryptor.class);
        when(ossSettingService.resolveRuntimeSetting()).thenReturn(runtimeSetting(root, true));
        when(cryptor.readAll(any())).thenReturn("plain".getBytes(StandardCharsets.UTF_8));
        when(cryptor.encrypt("plain".getBytes(StandardCharsets.UTF_8))).thenReturn("cipher".getBytes(StandardCharsets.UTF_8));
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props, ossSettingService, cryptor);

        String storagePath = storage.storeBytes("encrypted/file.bin", "plain".getBytes(StandardCharsets.UTF_8), "application/octet-stream");

        assertThat(storagePath).isEqualTo("local:encrypted/file.bin");
        assertThat(Files.readString(root.resolve("encrypted/file.bin"))).isEqualTo("cipher");
    }

    @Test
    void shouldLoadEncryptedContentWhenRuntimeSettingRequiresEncryption() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        Path file = root.resolve("encrypted-load.bin");
        Files.writeString(file, "cipher");
        AttachmentProperties props = new AttachmentProperties();
        OssSettingService ossSettingService = mock(OssSettingService.class);
        AttachmentContentCryptor cryptor = mock(AttachmentContentCryptor.class);
        when(ossSettingService.resolveRuntimeSetting()).thenReturn(runtimeSetting(root, true));
        when(cryptor.decrypt("cipher".getBytes(StandardCharsets.UTF_8))).thenReturn("plain".getBytes(StandardCharsets.UTF_8));
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props, ossSettingService, cryptor);

        var resource = storage.load("local:encrypted-load.bin");

        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("plain");
    }

    @Test
    void shouldRejectEncryptedStorageWhenCryptorIsMissing() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());
        props.getStorage().getS3().setEncryptedStorage(true);
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        assertThatThrownBy(() -> storage.storeBytes("missing-cryptor.bin", "plain".getBytes(StandardCharsets.UTF_8), "text/plain"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件加密组件未配置");
    }

    @Test
    void shouldWrapEncryptedReadIoFailure() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        Path directory = root.resolve("directory-target");
        Files.createDirectories(directory);
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());
        props.getStorage().getS3().setEncryptedStorage(true);
        AttachmentContentCryptor cryptor = mock(AttachmentContentCryptor.class);
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props, null, cryptor);

        assertThatThrownBy(() -> storage.load("local:directory-target"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件读取失败");
    }

    @Test
    void shouldRejectInvalidLocalRootPath() {
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath("\0bad-path");
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        assertThatThrownBy(() -> storage.load("local:any.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地附件存储路径配置错误");
    }

    @Test
    void shouldRejectLegacyPathOutsideRoot() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        Path outside = Files.createTempFile("outside-attachment", ".txt");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        assertThatThrownBy(() -> storage.load(outside.toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地附件存储路径非法");
    }

    @Test
    void shouldRejectDamagedStoragePath() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        assertThatThrownBy(() -> storage.load("\0bad-storage-path"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地附件存储路径损坏");
    }

    @Test
    void shouldPropagateNullPointerWhenLegacyPathIsNull() throws IOException {
        Path root = Files.createTempDirectory("attachment-test");
        AttachmentProperties props = new AttachmentProperties();
        props.getStorage().getLocal().setPath(root.toString());
        LocalAttachmentStorage storage = new LocalAttachmentStorage(props);

        assertThatThrownBy(() -> storage.load(null))
                .isInstanceOf(NullPointerException.class);
    }

    private OssSettingService.ResolvedOssSetting runtimeSetting(Path root, boolean encryptedStorage) {
        return new OssSettingService.ResolvedOssSetting(
                "local",
                "",
                root.toString(),
                new AttachmentProperties.S3(),
                encryptedStorage,
                false
        );
    }
}
