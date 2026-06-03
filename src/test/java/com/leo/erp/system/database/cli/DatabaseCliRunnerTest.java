package com.leo.erp.system.database.cli;

import com.leo.erp.system.database.service.DatabaseBackupService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.*;

class DatabaseCliRunnerTest {

    @Test
    void shouldExport_whenDbExportOptionPresent() throws Exception {
        var backupService = mock(DatabaseBackupService.class);
        var runner = new DatabaseCliRunner(backupService);
        var args = mock(ApplicationArguments.class);
        when(args.containsOption("db-export")).thenReturn(true);
        when(args.getOptionValues("db-export")).thenReturn(List.of("/tmp/export.sql"));

        runner.run(args);

        verify(backupService).exportBackup(Path.of("/tmp/export.sql"));
    }

    @Test
    void shouldImport_whenDbImportOptionPresent() throws Exception {
        var backupService = mock(DatabaseBackupService.class);
        var runner = new DatabaseCliRunner(backupService);
        var args = mock(ApplicationArguments.class);
        when(args.containsOption("db-export")).thenReturn(false);
        when(args.containsOption("db-import")).thenReturn(true);
        when(args.getOptionValues("db-import")).thenReturn(List.of("/tmp/import.sql"));

        runner.run(args);

        verify(backupService).importBackup(Path.of("/tmp/import.sql"));
    }

    @Test
    void shouldDoNothing_whenNoOptionPresent() throws Exception {
        var backupService = mock(DatabaseBackupService.class);
        var runner = new DatabaseCliRunner(backupService);
        var args = mock(ApplicationArguments.class);
        when(args.containsOption("db-export")).thenReturn(false);
        when(args.containsOption("db-import")).thenReturn(false);

        runner.run(args);

        verifyNoInteractions(backupService);
    }
}
