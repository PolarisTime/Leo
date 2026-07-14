package com.leo.erp.system.schedule.service;

import com.leo.erp.attachment.service.AttachmentManifestExportService;
import com.leo.erp.system.database.service.DatabaseExportTaskService;
import com.leo.erp.system.schedule.config.MaintenanceScheduleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    void shouldSkipDatabaseBackup_whenDisabled() throws Exception {
        properties.setEnabled(false);

        tasks().runDatabaseBackup();

        verify(scheduledDatabaseBackupService, never()).createBackupAndCleanup(anyInt());
    }

    @Test
    void shouldSkipArchiving_whenDisabled() throws Exception {
        properties.setEnabled(false);

        tasks().runOperationLogArchive();

        verify(operationLogArchiveService, never()).archiveBefore(any(), any(), anyInt());
    }

    @Test
    void shouldSkipCleanup_whenDisabled() {
        properties.setEnabled(false);

        tasks().runExportTaskCleanup();

        verify(databaseExportTaskService, never()).cleanupExpiredTasks();
    }

    @Test
    void shouldSkipDatabaseBackup_whenTaskDisabled() throws Exception {
        properties.getDatabaseBackup().setEnabled(false);

        tasks().runDatabaseBackup();

        verify(scheduledDatabaseBackupService, never()).createBackupAndCleanup(anyInt());
    }

    @Test
    void shouldSkipOperationLogArchive_whenTaskDisabled() throws Exception {
        properties.getOperationLogArchive().setEnabled(false);

        tasks().runOperationLogArchive();

        verify(operationLogArchiveService, never()).archiveBefore(any(), any(), anyInt());
    }

    @Test
    void shouldSkipExportTaskCleanup_whenTaskDisabled() {
        properties.getExportTaskCleanup().setEnabled(false);

        tasks().runExportTaskCleanup();

        verify(databaseExportTaskService, never()).cleanupExpiredTasks();
    }

    @Test
    void shouldRunDatabaseBackup_whenEnabled() throws Exception {
        properties.getDatabaseBackup().setEnabled(true);

        tasks().runDatabaseBackup();

        verify(scheduledDatabaseBackupService).createBackupAndCleanup(30);
    }

    @Test
    void shouldSkipDatabaseBackup_whenAlreadyRunning() throws Exception {
        properties.getDatabaseBackup().setEnabled(true);
        var tasks = tasks();
        markRunning(tasks, "databaseBackupRunning");

        tasks.runDatabaseBackup();

        verify(scheduledDatabaseBackupService, never()).createBackupAndCleanup(anyInt());
    }

    @Test
    void shouldRunOperationLogArchiveWithNormalizedRetentionAndConfiguredPath() throws Exception {
        properties.getOperationLogArchive().setEnabled(true);
        properties.getOperationLogArchive().setRetentionDays(0);
        properties.getOperationLogArchive().setBatchSize(25);
        properties.getOperationLogArchive().setArchivePath("/tmp/leo-operation-log-test");
        LocalDateTime lowerBound = LocalDateTime.now().minusDays(1).minusSeconds(2);

        tasks().runOperationLogArchive();

        LocalDateTime upperBound = LocalDateTime.now().minusDays(1).plusSeconds(2);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(operationLogArchiveService).archiveBefore(cutoffCaptor.capture(), pathCaptor.capture(), eq(25));
        assertThat(cutoffCaptor.getValue()).isBetween(lowerBound, upperBound);
        assertThat(pathCaptor.getValue()).isEqualTo(Path.of("/tmp/leo-operation-log-test"));
    }

    @Test
    void shouldSkipOperationLogArchive_whenAlreadyRunning() throws Exception {
        properties.getOperationLogArchive().setEnabled(true);
        var tasks = tasks();
        markRunning(tasks, "operationLogArchiveRunning");

        tasks.runOperationLogArchive();

        verify(operationLogArchiveService, never()).archiveBefore(any(), any(), anyInt());
    }

    @Test
    void shouldRunExportTaskCleanup_whenEnabled() {
        properties.getExportTaskCleanup().setEnabled(true);

        tasks().runExportTaskCleanup();

        verify(databaseExportTaskService).cleanupExpiredTasks();
    }

    @Test
    void shouldSkipExportTaskCleanup_whenAlreadyRunning() throws Exception {
        properties.getExportTaskCleanup().setEnabled(true);
        var tasks = tasks();
        markRunning(tasks, "exportTaskCleanupRunning");

        tasks.runExportTaskCleanup();

        verify(databaseExportTaskService, never()).cleanupExpiredTasks();
    }

    @Test
    void shouldHandleDatabaseBackupException() throws Exception {
        properties.getDatabaseBackup().setEnabled(true);
        doThrow(new RuntimeException("test error")).when(scheduledDatabaseBackupService).createBackupAndCleanup(30);

        tasks().runDatabaseBackup();
        // exception should be caught and logged, not propagated
    }

    @Test
    void shouldHandleOperationLogArchiveException() throws Exception {
        properties.getOperationLogArchive().setEnabled(true);
        doThrow(new RuntimeException("archive error")).when(operationLogArchiveService)
                .archiveBefore(any(), any(), anyInt());

        tasks().runOperationLogArchive();

        verify(operationLogArchiveService).archiveBefore(any(), any(), eq(1000));
    }

    @Test
    void shouldHandleExportTaskCleanupException() {
        properties.getExportTaskCleanup().setEnabled(true);
        doThrow(new RuntimeException("cleanup error")).when(databaseExportTaskService).cleanupExpiredTasks();

        tasks().runExportTaskCleanup();

        verify(databaseExportTaskService).cleanupExpiredTasks();
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
    void shouldSkipRedisCacheHealthCheck_whenMaintenanceDisabled() {
        properties.setEnabled(false);

        tasks().runRedisCacheHealthCheck();

        verify(redisCacheHealthCheckService, never()).verifyAndRefreshCaches();
    }

    @Test
    void shouldSkipRedisCacheHealthCheck_whenAlreadyRunning() throws Exception {
        properties.getRedisCacheHealthCheck().setEnabled(true);
        var tasks = tasks();
        markRunning(tasks, "redisCacheHealthCheckRunning");

        tasks.runRedisCacheHealthCheck();

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
    void shouldSkipAttachmentManifestExport_whenMaintenanceDisabled() {
        properties.setEnabled(false);

        tasks().runAttachmentManifestExport();

        verify(attachmentManifestExportService, never()).exportDaily();
    }

    @Test
    void shouldSkipAttachmentManifestExport_whenAlreadyRunning() throws Exception {
        properties.getAttachmentManifestExport().setEnabled(true);
        var tasks = tasks();
        markRunning(tasks, "attachmentManifestExportRunning");

        tasks.runAttachmentManifestExport();

        verify(attachmentManifestExportService, never()).exportDaily();
    }

    @Test
    void shouldHandleAttachmentManifestExportException() {
        properties.getAttachmentManifestExport().setEnabled(true);
        doThrow(new RuntimeException("test error")).when(attachmentManifestExportService).exportDaily();

        tasks().runAttachmentManifestExport();
        // exception should be caught and logged, not propagated
    }

    private void markRunning(MaintenanceScheduledTasks tasks, String fieldName) throws Exception {
        var field = MaintenanceScheduledTasks.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicBoolean) field.get(tasks)).set(true);
    }
}
