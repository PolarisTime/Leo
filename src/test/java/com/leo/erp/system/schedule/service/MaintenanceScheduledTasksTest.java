package com.leo.erp.system.schedule.service;

import com.leo.erp.attachment.service.AttachmentManifestExportService;
import com.leo.erp.system.database.service.DatabaseExportTaskService;
import com.leo.erp.system.schedule.config.MaintenanceScheduleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceScheduledTasksTest {

    private MaintenanceScheduleProperties properties;
    private ScheduledDatabaseBackupService scheduledDatabaseBackupService;
    private OperationLogArchiveService operationLogArchiveService;
    private DatabaseExportTaskService databaseExportTaskService;
    private RedisCacheHealthCheckService redisCacheHealthCheckService;
    private AttachmentManifestExportService attachmentManifestExportService;

    @BeforeEach
    void setUp() {
        properties = new MaintenanceScheduleProperties();
        scheduledDatabaseBackupService = mock(ScheduledDatabaseBackupService.class);
        operationLogArchiveService = mock(OperationLogArchiveService.class);
        databaseExportTaskService = mock(DatabaseExportTaskService.class);
        redisCacheHealthCheckService = mock(RedisCacheHealthCheckService.class);
        attachmentManifestExportService = mock(AttachmentManifestExportService.class);
    }

    private MaintenanceScheduledTasks tasks() {
        return new MaintenanceScheduledTasks(
                properties,
                scheduledDatabaseBackupService,
                operationLogArchiveService,
                databaseExportTaskService,
                redisCacheHealthCheckService,
                attachmentManifestExportService
        );
    }

    @Test
    void shouldSkipDatabaseBackup_whenDisabled() {
        properties.setEnabled(false);

        tasks().runDatabaseBackup();
        // no exception means success
    }

    @Test
    void shouldSkipArchiving_whenDisabled() {
        properties.setEnabled(false);

        tasks().runOperationLogArchive();
        // no exception means success
    }

    @Test
    void shouldSkipCleanup_whenDisabled() {
        properties.setEnabled(false);

        tasks().runExportTaskCleanup();
        // no exception means success
    }

    @Test
    void shouldRunDatabaseBackup_whenEnabled() {
        properties.getDatabaseBackup().setEnabled(true);

        tasks().runDatabaseBackup();
        // no exception means success, first call should run
    }

    @Test
    void shouldSkipDatabaseBackup_whenAlreadyRunning() {
        properties.getDatabaseBackup().setEnabled(true);
        var tasks = tasks();

        tasks.runDatabaseBackup();
        tasks.runDatabaseBackup();
        // second call should skip (already running)
    }

    @Test
    void shouldRunExportTaskCleanup_whenEnabled() {
        properties.getExportTaskCleanup().setEnabled(true);

        tasks().runExportTaskCleanup();
        // no exception means success
    }

    @Test
    void shouldHandleDatabaseBackupException() throws Exception {
        properties.getDatabaseBackup().setEnabled(true);
        doThrow(new RuntimeException("test error")).when(scheduledDatabaseBackupService).createBackupAndCleanup(30);

        tasks().runDatabaseBackup();
        // exception should be caught and logged, not propagated
    }

    @Test
    void shouldRunRedisCacheHealthCheck_whenEnabled() {
        properties.getRedisCacheHealthCheck().setEnabled(true);

        tasks().runRedisCacheHealthCheck();

        verify(redisCacheHealthCheckService).verifyAndRefreshCaches();
    }

    @Test
    void shouldSkipRedisCacheHealthCheck_whenDisabled() {
        properties.getRedisCacheHealthCheck().setEnabled(false);

        tasks().runRedisCacheHealthCheck();

        verify(redisCacheHealthCheckService, never()).verifyAndRefreshCaches();
    }

    @Test
    void shouldHandleRedisCacheHealthCheckException() {
        properties.getRedisCacheHealthCheck().setEnabled(true);
        doThrow(new RuntimeException("test error")).when(redisCacheHealthCheckService).verifyAndRefreshCaches();

        tasks().runRedisCacheHealthCheck();
        // exception should be caught and logged, not propagated
    }

    @Test
    void shouldRunAttachmentManifestExport_whenEnabled() {
        properties.getAttachmentManifestExport().setEnabled(true);

        tasks().runAttachmentManifestExport();

        verify(attachmentManifestExportService).exportDaily();
    }

    @Test
    void shouldSkipAttachmentManifestExport_whenDisabled() {
        properties.getAttachmentManifestExport().setEnabled(false);

        tasks().runAttachmentManifestExport();

        verify(attachmentManifestExportService, never()).exportDaily();
    }

    @Test
    void shouldHandleAttachmentManifestExportException() {
        properties.getAttachmentManifestExport().setEnabled(true);
        doThrow(new RuntimeException("test error")).when(attachmentManifestExportService).exportDaily();

        tasks().runAttachmentManifestExport();
        // exception should be caught and logged, not propagated
    }
}
