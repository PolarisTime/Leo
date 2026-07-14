package com.leo.erp.system.database.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ExternalProcessRunner;
import com.leo.erp.system.database.config.DatabaseBackupProperties;
import com.leo.erp.system.database.config.DatabaseImportProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseBackupServiceTest {

    @Test
    void shouldExportBackupWithDefaultCredentials() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, true);

        Path targetFile = Files.createTempFile("backup-export-", ".sql");
        targetFile.toFile().deleteOnExit();

        Path result = service.exportBackup(targetFile);

        assertThat(result).isEqualTo(targetFile);
        assertThat(processRunner.actions).containsExactly("pg_dump");
        assertThat(processRunner.commands.get(0))
                .contains("pg_dump", "--host=localhost", "--port=5432", "--username=leo", "--dbname=leo");
        assertThat(processRunner.commands.get(0))
                .contains("--file=" + targetFile.toAbsolutePath());
        assertThat(processRunner.passwords).containsExactly("secret");
    }

    @Test
    void shouldCreateTempExportFile_whenNoTargetFileProvided() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, true);

        Path result = service.exportBackup();

        assertThat(result.getFileName().toString()).startsWith("leo-backup-");
        assertThat(result.getFileName().toString()).endsWith(".sql");
        assertThat(processRunner.actions).containsExactly("pg_dump");
    }

    @Test
    void shouldDeleteTargetAndRethrow_whenExportProcessFails() throws Exception {
        FailingProcessRunner processRunner = new FailingProcessRunner("pg_dump");
        DatabaseBackupService service = newService(processRunner, true);
        Path targetFile = Files.createTempFile("backup-export-failed-", ".sql");
        Files.writeString(targetFile, "old content");

        assertThatThrownBy(() -> service.exportBackup(targetFile, "leo", "secret"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("pg_dump failed");

        assertThat(Files.exists(targetFile)).isFalse();
    }

    @Test
    void shouldAutoBackupBeforeImportWhenEnabled() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, true);

        Path tempFile = Files.createTempFile("backup-test-", ".sql");
        tempFile.toFile().deleteOnExit();
        Files.write(tempFile, "select 1;".getBytes(StandardCharsets.UTF_8));
        service.importBackup(tempFile, "leo", "secret");

        assertThat(processRunner.actions).containsExactly("pg_dump", "psql");
    }

    @Test
    void shouldSkipAutoBackupBeforeImportWhenDisabled() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);

        Path tempFile = Files.createTempFile("backup-test-", ".sql");
        tempFile.toFile().deleteOnExit();
        Files.write(tempFile, "select 1;".getBytes(StandardCharsets.UTF_8));
        service.importBackup(tempFile, "leo", "secret");

        assertThat(processRunner.actions).containsExactly("psql");
    }

    @Test
    void shouldFailFastAndNotStartProcess_whenImportDisabled() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, true, false);
        Path tempFile = Files.createTempFile("backup-import-disabled-", ".sql");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, "leo", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据库备份导入已禁用");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldCheckImportSwitchBeforeImportFileValidation() {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, true, false);

        assertThatThrownBy(() -> service.importBackup(Path.of("/tmp/leo-missing-import.sql"), "leo", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据库备份导入已禁用");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldImportBackupWithDefaultCredentials() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-", ".dump");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        service.importBackup(tempFile);

        assertThat(processRunner.actions).containsExactly("psql");
        assertThat(processRunner.commands.get(0))
                .contains("psql", "--host=localhost", "--port=5432", "--username=leo", "--dbname=leo");
        assertThat(processRunner.commands.get(0))
                .contains("--file=" + tempFile.toAbsolutePath(), "--single-transaction", "--set=ON_ERROR_STOP=on");
        assertThat(processRunner.passwords).containsExactly("secret");
    }

    @Test
    void shouldThrowValidationError_whenImportFileIsMissing() {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);

        assertThatThrownBy(() -> service.importBackup(Path.of("/tmp/leo-missing-import.sql"), "leo", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件不存在");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenImportFileIsNull() {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);

        assertThatThrownBy(() -> service.importBackup(null, "leo", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件不存在");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenImportFileExtensionUnsupported() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-", ".txt");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, "leo", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenImportFileExceedsSizeLimit() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-large-", ".sql");
        tempFile.toFile().deleteOnExit();
        try (RandomAccessFile file = new RandomAccessFile(tempFile.toFile(), "rw")) {
            file.setLength(500L * 1024 * 1024 + 1);
        }

        assertThatThrownBy(() -> service.importBackup(tempFile, "leo", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入文件超过大小限制");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenImportFileSizeCannotBeRead() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-unreadable-", ".sql");
        tempFile.toFile().deleteOnExit();

        try (var files = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.size(tempFile)).thenThrow(new IOException("size failed"));

            assertThatThrownBy(() -> service.importBackup(tempFile, "leo", "secret"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无法读取导入文件");
        }

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenDatabaseUsernameIsBlank() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-", ".pgdump");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, " ", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据库用户名不能为空");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenDatabaseUsernameIsNull() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-", ".pgdump");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, null, "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据库用户名不能为空");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenDatabasePasswordIsBlank() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-", ".sql");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, "leo", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据库密码不能为空");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldThrowValidationError_whenDatabasePasswordIsNull() throws Exception {
        RecordingProcessRunner processRunner = new RecordingProcessRunner();
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-", ".sql");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, "leo", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据库密码不能为空");

        assertThat(processRunner.actions).isEmpty();
    }

    @Test
    void shouldWrapImportFailure_whenAutoBackupWasCreated() throws Exception {
        FailingProcessRunner processRunner = new FailingProcessRunner("psql");
        DatabaseBackupService service = newService(processRunner, true);
        Path tempFile = Files.createTempFile("backup-import-", ".sql");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, "leo", "secret"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("数据库导入失败，已保留自动备份文件")
                .hasCauseInstanceOf(IOException.class);

        assertThat(processRunner.actions).containsExactly("pg_dump", "psql");
    }

    @Test
    void shouldRethrowImportFailure_whenAutoBackupDisabled() throws Exception {
        FailingProcessRunner processRunner = new FailingProcessRunner("psql");
        DatabaseBackupService service = newService(processRunner, false);
        Path tempFile = Files.createTempFile("backup-import-", ".sql");
        tempFile.toFile().deleteOnExit();
        Files.writeString(tempFile, "select 1;");

        assertThatThrownBy(() -> service.importBackup(tempFile, "leo", "secret"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("psql failed");

        assertThat(processRunner.actions).containsExactly("psql");
    }

    private DatabaseBackupService newService(ExternalProcessRunner processRunner, boolean autoBackupBeforeImport) {
        return newService(processRunner, autoBackupBeforeImport, true);
    }

    private DatabaseBackupService newService(ExternalProcessRunner processRunner,
                                             boolean autoBackupBeforeImport,
                                             boolean importEnabled) {
        DatabaseBackupProperties properties = new DatabaseBackupProperties();
        properties.setAutoBackupBeforeImport(autoBackupBeforeImport);
        DatabaseImportProperties importProperties = new DatabaseImportProperties();
        importProperties.setEnabled(importEnabled);
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:postgresql://localhost:5432/leo");
        dataSourceProperties.setUsername("leo");
        dataSourceProperties.setPassword("secret");
        return new DatabaseBackupService(processRunner, properties, importProperties, dataSourceProperties);
    }

    private static final class RecordingProcessRunner extends ExternalProcessRunner {

        private final List<String> actions = new ArrayList<>();
        private final List<List<String>> commands = new ArrayList<>();
        private final List<String> passwords = new ArrayList<>();

        @Override
        public ProcessResult run(ProcessBuilder processBuilder, Duration timeout, String actionName) throws IOException {
            actions.add(actionName);
            commands.add(processBuilder.command());
            passwords.add(processBuilder.environment().get("PGPASSWORD"));
            return new ProcessResult(0, "ok");
        }
    }

    private static final class FailingProcessRunner extends ExternalProcessRunner {

        private final String failedAction;
        private final List<String> actions = new ArrayList<>();

        private FailingProcessRunner(String failedAction) {
            this.failedAction = failedAction;
        }

        @Override
        public ProcessResult run(ProcessBuilder processBuilder, Duration timeout, String actionName) throws IOException {
            actions.add(actionName);
            if (failedAction.equals(actionName)) {
                throw new IOException(actionName + " failed");
            }
            return new ProcessResult(0, "ok");
        }
    }
}
