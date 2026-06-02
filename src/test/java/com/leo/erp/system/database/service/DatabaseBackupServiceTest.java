package com.leo.erp.system.database.service;

import com.leo.erp.common.support.ExternalProcessRunner;
import com.leo.erp.system.database.config.DatabaseBackupProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseBackupServiceTest {

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

    private DatabaseBackupService newService(RecordingProcessRunner processRunner, boolean autoBackupBeforeImport) {
        DatabaseBackupProperties properties = new DatabaseBackupProperties();
        properties.setAutoBackupBeforeImport(autoBackupBeforeImport);
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:postgresql://localhost:5432/leo");
        dataSourceProperties.setUsername("leo");
        dataSourceProperties.setPassword("secret");
        return new DatabaseBackupService(processRunner, properties, dataSourceProperties);
    }

    private static final class RecordingProcessRunner extends ExternalProcessRunner {

        private final List<String> actions = new ArrayList<>();

        @Override
        public ProcessResult run(ProcessBuilder processBuilder, Duration timeout, String actionName) throws IOException {
            actions.add(actionName);
            return new ProcessResult(0, "ok");
        }
    }
}
