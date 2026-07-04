package com.leo.erp.system.database.service;

import lombok.extern.slf4j.Slf4j;
import com.leo.erp.common.support.ExternalProcessRunner;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PostgresJdbcUrlParser;
import com.leo.erp.system.database.config.DatabaseBackupProperties;
import com.leo.erp.system.database.config.DatabaseImportProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class DatabaseBackupService {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final ExternalProcessRunner processRunner;
    private final DatabaseBackupProperties backupProperties;
    private final DatabaseImportProperties importProperties;
    private final DataSourceProperties dataSourceProperties;

    public DatabaseBackupService(ExternalProcessRunner processRunner,
                                 DatabaseBackupProperties backupProperties,
                                 DatabaseImportProperties importProperties,
                                 DataSourceProperties dataSourceProperties) {
        this.processRunner = processRunner;
        this.backupProperties = backupProperties;
        this.importProperties = importProperties;
        this.dataSourceProperties = dataSourceProperties;
    }

    public Path exportBackup(Path targetFile) throws IOException, InterruptedException {
        return exportBackup(targetFile, dataSourceProperties.getUsername(), dataSourceProperties.getPassword());
    }

    public Path exportBackup(Path targetFile, String databaseUsername, String databasePassword) throws IOException, InterruptedException {
        try {
            runPgDump(targetFile, databaseUsername, databasePassword);
            return targetFile;
        } catch (IOException ex) {
            Files.deleteIfExists(targetFile);
            throw ex;
        }
    }

    public Path exportBackup(String databaseUsername, String databasePassword) throws IOException, InterruptedException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        Path tempFile = Files.createTempFile("leo-backup-" + timestamp + "-", ".sql");
        return exportBackup(tempFile, databaseUsername, databasePassword);
    }

    public Path exportBackup() throws IOException, InterruptedException {
        return exportBackup(dataSourceProperties.getUsername(), dataSourceProperties.getPassword());
    }

    public void importBackup(Path sqlFile) throws IOException, InterruptedException {
        importBackup(sqlFile, dataSourceProperties.getUsername(), dataSourceProperties.getPassword());
    }

    public void importBackup(Path sqlFile, String databaseUsername, String databasePassword) throws IOException, InterruptedException {
        validateImportEnabled();
        validateImportFile(sqlFile);
        validateDatabaseCredentials(databaseUsername, databasePassword);
        Path autoBackup = null;
        if (backupProperties.isAutoBackupBeforeImport()) {
            autoBackup = exportBackup(databaseUsername, databasePassword);
            log.info("导入前自动备份完成: {} ({}KB)", autoBackup.toAbsolutePath(), Files.size(autoBackup) / 1024);
        } else {
            log.info("数据库导入前自动备份已禁用，跳过自动备份");
        }

        PostgresJdbcUrlParser.ParsedJdbcUrl jdbcUrl = extractJdbcUrl();
        String[] cmd = {
                backupProperties.getPsqlCommand(),
                "--host=" + jdbcUrl.host(),
                "--port=" + jdbcUrl.port(),
                "--username=" + databaseUsername.trim(),
                "--dbname=" + jdbcUrl.database(),
                "--file=" + sqlFile.toAbsolutePath(),
                "--single-transaction",
                "--set=ON_ERROR_STOP=on"
        };

        log.info("开始导入数据库: {} ({}KB)", sqlFile.getFileName(), Files.size(sqlFile) / 1024);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGPASSWORD", databasePassword.trim());
        pb.redirectErrorStream(true);

        try {
            processRunner.run(pb, TIMEOUT, "psql");
        } catch (IOException ex) {
            if (autoBackup != null) {
                log.error("数据库导入失败，已保留自动备份文件: {}", autoBackup.toAbsolutePath(), ex);
                throw new IOException("数据库导入失败，已保留自动备份文件", ex);
            }
            throw ex;
        }

        log.info("数据库导入完成: {}", sqlFile.getFileName());
    }

    private static final Set<String> ALLOWED_IMPORT_EXTENSIONS = Set.of(".sql", ".dump", ".pgdump");
    private static final long MAX_IMPORT_FILE_SIZE = 500L * 1024 * 1024;

    private void validateImportEnabled() {
        if (!importProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "数据库备份导入已禁用");
        }
    }

    private void validateImportFile(Path file) {
        if (file == null || !Files.exists(file)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入文件不存在");
        }
        try {
            if (Files.size(file) > MAX_IMPORT_FILE_SIZE) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入文件超过大小限制 (最大 500MB)");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法读取导入文件");
        }
        String filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_IMPORT_EXTENSIONS.stream().anyMatch(filename::endsWith);
        if (!allowed) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的文件类型，仅允许 .sql / .dump / .pgdump");
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
