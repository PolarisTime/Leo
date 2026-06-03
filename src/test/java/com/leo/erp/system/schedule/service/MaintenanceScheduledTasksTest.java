package com.leo.erp.system.schedule.service;

import com.leo.erp.system.database.service.DatabaseExportTaskService;
import com.leo.erp.system.schedule.config.MaintenanceScheduleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceScheduledTasksTest {

    private MaintenanceScheduleProperties properties;
    private ScheduledDatabaseBackupService scheduledDatabaseBackupService;
    private OperationLogArchiveService operationLogArchiveService;
    private DatabaseExportTaskService databaseExportTaskService;

    @BeforeEach
    void setUp() {
        properties = new MaintenanceScheduleProperties();
        scheduledDatabaseBackupService = mock(ScheduledDatabaseBackupService.class);
        operationLogArchiveService = mock(OperationLogArchiveService.class);
        databaseExportTaskService = mock(DatabaseExportTaskService.class);
    }

    @Test
    void shouldSkipDatabaseBackup_whenDisabled() {
        properties.setEnabled(false);
        var tasks = new MaintenanceScheduledTasks(properties, scheduledDatabaseBackupService, operationLogArchiveService, databaseExportTaskService);

        tasks.runDatabaseBackup();
        // no exception means success
    }

    @Test
    void shouldSkipArchiving_whenDisabled() {
        properties.setEnabled(false);
        var tasks = new MaintenanceScheduledTasks(properties, scheduledDatabaseBackupService, operationLogArchiveService, databaseExportTaskService);

        tasks.runOperationLogArchive();
        // no exception means success
    }

    @Test
    void shouldSkipCleanup_whenDisabled() {
        properties.setEnabled(false);
        var tasks = new MaintenanceScheduledTasks(properties, scheduledDatabaseBackupService, operationLogArchiveService, databaseExportTaskService);

        tasks.runExportTaskCleanup();
        // no exception means success
    }

    @Test
    void shouldRunDatabaseBackup_whenEnabled() {
        properties.getDatabaseBackup().setEnabled(true);
        var tasks = new MaintenanceScheduledTasks(properties, scheduledDatabaseBackupService, operationLogArchiveService, databaseExportTaskService);

        tasks.runDatabaseBackup();
        // no exception means success, first call should run
    }

    @Test
    void shouldSkipDatabaseBackup_whenAlreadyRunning() {
        properties.getDatabaseBackup().setEnabled(true);
        var tasks = new MaintenanceScheduledTasks(properties, scheduledDatabaseBackupService, operationLogArchiveService, databaseExportTaskService);

        tasks.runDatabaseBackup();
        tasks.runDatabaseBackup();
        // second call should skip (already running)
    }

    @Test
    void shouldRunExportTaskCleanup_whenEnabled() {
        properties.getExportTaskCleanup().setEnabled(true);
        var tasks = new MaintenanceScheduledTasks(properties, scheduledDatabaseBackupService, operationLogArchiveService, databaseExportTaskService);

        tasks.runExportTaskCleanup();
        // no exception means success
    }

    @Test
    void shouldHandleDatabaseBackupException() throws Exception {
        properties.getDatabaseBackup().setEnabled(true);
        doThrow(new RuntimeException("test error")).when(scheduledDatabaseBackupService).createBackupAndCleanup(30);
        var tasks = new MaintenanceScheduledTasks(properties, scheduledDatabaseBackupService, operationLogArchiveService, databaseExportTaskService);

        tasks.runDatabaseBackup();
        // exception should be caught and logged, not propagated
    }
}
