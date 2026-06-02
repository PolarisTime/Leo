package com.leo.erp.system.schedule.service;

import com.leo.erp.system.database.config.DatabaseBackupProperties;
import com.leo.erp.system.database.service.DatabaseBackupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ScheduledDatabaseBackupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateCompressedBackupAndCleanupExpiredFiles() throws Exception {
        DatabaseBackupService backupService = mock(DatabaseBackupService.class);
        doAnswer(invocation -> {
            Path targetFile = invocation.getArgument(0);
            Files.writeString(targetFile, "select 1;", StandardCharsets.UTF_8);
            return targetFile;
        }).when(backupService).exportBackup(any(Path.class), eq("leo"), eq("secret"));

        DatabaseBackupProperties backupProperties = new DatabaseBackupProperties();
        backupProperties.setStoragePath(tempDir.toString());

        Path expiredBackup = tempDir.resolve("leo-scheduled-backup-old.sql.gz");
        Files.writeString(expiredBackup, "old", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(expiredBackup, FileTime.from(Instant.now().minusSeconds(31L * 24L * 60L * 60L)));

        ScheduledDatabaseBackupService service = new ScheduledDatabaseBackupService(backupService, backupProperties);
        ReflectionTestUtils.setField(service, "datasourceUsername", "leo");
        ReflectionTestUtils.setField(service, "datasourcePassword", "secret");

        ScheduledDatabaseBackupService.BackupResult result = service.createBackupAndCleanup(30);

        assertThat(result.file()).exists();
        assertThat(result.file().getFileName().toString()).startsWith("leo-scheduled-backup-").endsWith(".sql.gz");
        assertThat(result.deletedFiles()).isEqualTo(1);
        assertThat(expiredBackup).doesNotExist();
        assertThat(readGzip(result.file())).isEqualTo("select 1;");
    }

    private String readGzip(Path file) throws Exception {
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(file))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
