package com.leo.erp.attachment.service.storage;

import com.leo.erp.attachment.config.AttachmentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndLoadUsingConfiguredRootPath() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.getStorage().getLocal().setPath(tempDir.toString());

        LocalAttachmentStorage storage = new LocalAttachmentStorage(properties);
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8));

        String storagePath = storage.store("prefix/2026/04/1/hello.txt", file);

        assertThat(storagePath).isEqualTo("local:prefix/2026/04/1/hello.txt");
        assertThat(Files.readString(tempDir.resolve("prefix/2026/04/1/hello.txt"))).isEqualTo("abc");
        assertThat(new String(storage.load(storagePath).getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("abc");
    }
}
