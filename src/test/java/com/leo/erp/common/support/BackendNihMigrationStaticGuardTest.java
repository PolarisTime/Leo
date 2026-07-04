package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackendNihMigrationStaticGuardTest {

    @Test
    void productionCodeShouldNotRetainManualGetOrLoadCachePath() throws IOException {
        assertThat(findProductionFilesContaining("getOrLoad(")).isEmpty();
    }

    @Test
    void productionCodeShouldNotRetainLegacySecretEncryptionPath() throws IOException {
        assertThat(findProductionFilesContaining("LegacySecretEncryptionEngine")).isEmpty();
        assertThat(findProductionFilesContaining("SecuritySecretEncryptionAlgorithm.LEGACY")).isEmpty();
        assertThat(findProductionFilesContaining("LEGACY(\"")).isEmpty();
    }

    private List<Path> findProductionFilesContaining(String pattern) throws IOException {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> contains(path, pattern))
                    .toList();
        }
    }

    private boolean contains(Path path, String pattern) {
        try {
            return Files.readString(path).contains(pattern);
        } catch (IOException ex) {
            throw new IllegalStateException("读取源码失败: " + path, ex);
        }
    }
}
