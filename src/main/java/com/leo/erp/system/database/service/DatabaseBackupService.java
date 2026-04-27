package com.leo.erp.system.database.service;

import com.leo.erp.common.support.ExternalProcessRunner;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PostgresJdbcUrlParser;
import com.leo.erp.system.database.config.DatabaseBackupProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final ExternalProcessRunner processRunner;
    private final DatabaseBackupProperties backupProperties;
    private final DataSourceProperties dataSourceProperties;

    public DatabaseBackupService(ExternalProcessRunner processRunner,
                                 DatabaseBackupProperties backupProperties,
                                 DataSourceProperties dataSourceProperties) {
        this.processRunner = processRunner;
        this.backupProperties = backupProperties;
        this.dataSourceProperties = dataSourceProperties;
    }

    public Path exportBackup() throws IOException, InterruptedException {
        return exportBackup(dataSourceProperties.getUsername(), dataSourceProperties.getPassword());
    }

    public Path exportBackup(String databaseUsername, String databasePassword) throws IOException, InterruptedException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        Path tempFile = Files.createTempFile("leo-backup-" + timestamp + "-", ".sql");
        try {
            runPgDump(tempFile, databaseUsername, databasePassword);
            return tempFile;
        } catch (IOException ex) {
            Files.deleteIfExists(tempFile);
            throw ex;
        }
    }

    public void importBackup(MultipartFile file, String databaseUsername, String databasePassword) throws IOException, InterruptedException {
        validateImportFile(file);
        validateDatabaseCredentials(databaseUsername, databasePassword);
        Path autoBackup = null;
        if (backupProperties.isAutoBackupBeforeImport()) {
            autoBackup = exportBackup(databaseUsername, databasePassword);
            log.info("导入前自动备份完成: {} ({}KB)", autoBackup.toAbsolutePath(), Files.size(autoBackup) / 1024);
        } else {
            log.info("数据库导入前自动备份已禁用，跳过自动备份");
        }

        Path uploadFile = Files.createTempFile("leo-import-", ".sql");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, uploadFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        PostgresJdbcUrlParser.ParsedJdbcUrl jdbcUrl = extractJdbcUrl();
        String[] cmd = {
                backupProperties.getPsqlCommand(),
                "--host=" + jdbcUrl.host(),
                "--port=" + jdbcUrl.port(),
                "--username=" + databaseUsername.trim(),
                "--dbname=" + jdbcUrl.database(),
                "--file=" + uploadFile.toAbsolutePath(),
                "--single-transaction",
                "--set=ON_ERROR_STOP=on"
        };

        log.info("开始导入数据库: {} ({}KB)", file.getOriginalFilename(), file.getSize() / 1024);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", databasePassword.trim());
        pb.redirectErrorStream(true);

        try {
            processRunner.run(pb, TIMEOUT, "psql");
        } catch (IOException ex) {
            if (autoBackup != null) {
                throw new IOException(ex.getMessage() + "，自动备份文件: " + autoBackup.toAbsolutePath(), ex);
            }
            throw ex;
        } finally {
            Files.deleteIfExists(uploadFile);
        }

        log.info("数据库导入完成: {}", file.getOriginalFilename());
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
    }

    private void validateDatabaseCredentials(String databaseUsername, String databasePassword) {
        if (databaseUsername == null || databaseUsername.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据库用户名不能为空");
        }
        if (databasePassword == null || databasePassword.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "数据库密码不能为空");
        }
    }

    private PostgresJdbcUrlParser.ParsedJdbcUrl extractJdbcUrl() {
        return PostgresJdbcUrlParser.parse(dataSourceProperties.getUrl());
    }

    private void runPgDump(Path targetFile, String databaseUsername, String databasePassword) throws IOException, InterruptedException {
        validateDatabaseCredentials(databaseUsername, databasePassword);

        String filename = targetFile.getFileName().toString();
        PostgresJdbcUrlParser.ParsedJdbcUrl jdbcUrl = extractJdbcUrl();
        String[] cmd = {
                backupProperties.getPgDumpCommand(),
                "--host=" + jdbcUrl.host(),
                "--port=" + jdbcUrl.port(),
                "--username=" + databaseUsername.trim(),
                "--dbname=" + jdbcUrl.database(),
                "--format=plain",
                "--no-owner",
                "--no-privileges",
                "--file=" + targetFile.toAbsolutePath()
        };

        log.info("开始导出数据库: pg_dump -> {}", filename);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", databasePassword.trim());
        pb.redirectErrorStream(true);
        processRunner.run(pb, TIMEOUT, "pg_dump");
        log.info("数据库导出完成: {} ({}KB)", filename, Files.size(targetFile) / 1024);
    }
}
