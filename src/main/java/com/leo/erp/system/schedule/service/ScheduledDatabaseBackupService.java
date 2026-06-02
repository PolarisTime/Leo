package com.leo.erp.system.schedule.service;

import com.leo.erp.system.database.config.DatabaseBackupProperties;
import com.leo.erp.system.database.service.DatabaseBackupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
public class ScheduledDatabaseBackupService {

    private static final String BACKUP_PREFIX = "leo-scheduled-backup-";
    private static final String BACKUP_SUFFIX = ".sql.gz";
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    private final DatabaseBackupService databaseBackupService;
    private final DatabaseBackupProperties backupProperties;

    public ScheduledDatabaseBackupService(DatabaseBackupService databaseBackupService,
                                          DatabaseBackupProperties backupProperties) {
        this.databaseBackupService = databaseBackupService;
        this.backupProperties = backupProperties;
    }

    public BackupResult createBackupAndCleanup(int retentionDays) throws IOException, InterruptedException {
        int normalizedRetentionDays = Math.max(1, retentionDays);
        Path storageDir = Path.of(backupProperties.getStoragePath());
        Files.createDirectories(storageDir);

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        Path sqlFile = storageDir.resolve(BACKUP_PREFIX + timestamp + ".sql");
        Path gzipTempFile = storageDir.resolve(BACKUP_PREFIX + timestamp + BACKUP_SUFFIX + ".tmp");
        Path gzipFile = storageDir.resolve(BACKUP_PREFIX + timestamp + BACKUP_SUFFIX);

        try {
            databaseBackupService.exportBackup(sqlFile, datasourceUsername, datasourcePassword);
            gzip(sqlFile, gzipTempFile);
            moveBackupFile(gzipTempFile, gzipFile);
            long fileSize = Files.size(gzipFile);
            int deletedFiles = cleanupExpiredBackups(storageDir, normalizedRetentionDays);
            cleanupStaleTempFiles(storageDir);
            log.info("定时数据库备份完成: file={}, size={} bytes, retentionDays={}, deletedFiles={}",
                    gzipFile, fileSize, normalizedRetentionDays, deletedFiles);
            return new BackupResult(gzipFile, fileSize, deletedFiles);
        } finally {
            Files.deleteIfExists(sqlFile);
            Files.deleteIfExists(gzipTempFile);
        }
    }

    private void gzip(Path sourceFile, Path targetFile) throws IOException {
        try (InputStream in = Files.newInputStream(sourceFile);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(targetFile))) {
            in.transferTo(out);
        }
    }

    private int cleanupExpiredBackups(Path storageDir, int retentionDays) throws IOException {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60L * 60L);
        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, BACKUP_PREFIX + "*" + BACKUP_SUFFIX)) {
            for (Path file : stream) {
                FileTime lastModifiedTime = Files.getLastModifiedTime(file);
                if (lastModifiedTime.toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    private void moveBackupFile(Path tempFile, Path targetFile) throws IOException {
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanupStaleTempFiles(Path storageDir) throws IOException {
        Instant cutoff = LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, BACKUP_PREFIX + "*.tmp")) {
            for (Path file : stream) {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    public record BackupResult(Path file, long fileSize, int deletedFiles) {
    }
}
