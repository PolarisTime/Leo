package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
