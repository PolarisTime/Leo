package com.leo.erp.system.schedule.service;

import com.leo.erp.attachment.service.AttachmentManifestExportService;
import com.leo.erp.system.database.service.DatabaseExportTaskService;
import com.leo.erp.system.schedule.config.MaintenanceScheduleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class MaintenanceScheduledTasks {

    private final MaintenanceScheduleProperties properties;
    private final ScheduledDatabaseBackupService scheduledDatabaseBackupService;
    private final OperationLogArchiveService operationLogArchiveService;
    private final DatabaseExportTaskService databaseExportTaskService;
    private final RedisCacheHealthCheckService redisCacheHealthCheckService;
    private final AttachmentManifestExportService attachmentManifestExportService;
    private final AtomicBoolean databaseBackupRunning = new AtomicBoolean(false);
    private final AtomicBoolean operationLogArchiveRunning = new AtomicBoolean(false);
    private final AtomicBoolean exportTaskCleanupRunning = new AtomicBoolean(false);
    private final AtomicBoolean redisCacheHealthCheckRunning = new AtomicBoolean(false);
    private final AtomicBoolean attachmentManifestExportRunning = new AtomicBoolean(false);

    public MaintenanceScheduledTasks(MaintenanceScheduleProperties properties,
                                     ScheduledDatabaseBackupService scheduledDatabaseBackupService,
                                     OperationLogArchiveService operationLogArchiveService,
                                     DatabaseExportTaskService databaseExportTaskService,
                                     RedisCacheHealthCheckService redisCacheHealthCheckService,
                                     AttachmentManifestExportService attachmentManifestExportService) {
        this.properties = properties;
        this.scheduledDatabaseBackupService = scheduledDatabaseBackupService;
        this.operationLogArchiveService = operationLogArchiveService;
        this.databaseExportTaskService = databaseExportTaskService;
        this.redisCacheHealthCheckService = redisCacheHealthCheckService;
        this.attachmentManifestExportService = attachmentManifestExportService;
    }

    @Scheduled(cron = "${leo.maintenance.database-backup.cron:0 15 2 * * *}", zone = "${leo.maintenance.zone:Asia/Shanghai}")
    public void runDatabaseBackup() {
        if (!properties.isEnabled() || !properties.getDatabaseBackup().isEnabled()) {
            return;
        }
        if (!databaseBackupRunning.compareAndSet(false, true)) {
            log.warn("跳过定时数据库备份：上一轮仍在执行");
            return;
        }
        try {
            scheduledDatabaseBackupService.createBackupAndCleanup(properties.getDatabaseBackup().getRetentionDays());
        } catch (Exception ex) {
            log.error("定时数据库备份失败", ex);
        } finally {
            databaseBackupRunning.set(false);
        }
    }

    @Scheduled(cron = "${leo.maintenance.operation-log-archive.cron:0 30 2 * * *}", zone = "${leo.maintenance.zone:Asia/Shanghai}")
    public void runOperationLogArchive() {
        if (!properties.isEnabled() || !properties.getOperationLogArchive().isEnabled()) {
            return;
        }
        if (!operationLogArchiveRunning.compareAndSet(false, true)) {
            log.warn("跳过操作日志归档：上一轮仍在执行");
            return;
        }
        try {
            int retentionDays = Math.max(1, properties.getOperationLogArchive().getRetentionDays());
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            Path archivePath = Path.of(properties.getOperationLogArchive().getArchivePath());
            operationLogArchiveService.archiveBefore(
                    cutoff,
                    archivePath,
                    properties.getOperationLogArchive().getBatchSize()
            );
        } catch (Exception ex) {
            log.error("操作日志归档失败", ex);
        } finally {
            operationLogArchiveRunning.set(false);
        }
    }

    @Scheduled(cron = "${leo.maintenance.export-task-cleanup.cron:0 45 2 * * *}", zone = "${leo.maintenance.zone:Asia/Shanghai}")
    public void runExportTaskCleanup() {
        if (!properties.isEnabled() || !properties.getExportTaskCleanup().isEnabled()) {
            return;
        }
        if (!exportTaskCleanupRunning.compareAndSet(false, true)) {
            log.warn("跳过数据库导出任务清理：上一轮仍在执行");
            return;
        }
        try {
            databaseExportTaskService.cleanupExpiredTasks();
        } catch (Exception ex) {
            log.error("数据库导出任务清理失败", ex);
        } finally {
            exportTaskCleanupRunning.set(false);
        }
    }

    @Scheduled(cron = "${leo.maintenance.redis-cache-health-check.cron:0 */5 * * * *}", zone = "${leo.maintenance.zone:Asia/Shanghai}")
    public void runRedisCacheHealthCheck() {
        if (!properties.isEnabled() || !properties.getRedisCacheHealthCheck().isEnabled()) {
            return;
        }
        if (!redisCacheHealthCheckRunning.compareAndSet(false, true)) {
            log.warn("跳过 Redis 缓存巡检：上一轮仍在执行");
            return;
        }
        try {
            redisCacheHealthCheckService.verifyAndRefreshCaches();
        } catch (Exception ex) {
            log.error("Redis 缓存巡检失败", ex);
        } finally {
            redisCacheHealthCheckRunning.set(false);
        }
    }

    @Scheduled(cron = "${leo.maintenance.attachment-manifest-export.cron:0 5 2 * * *}", zone = "${leo.maintenance.zone:Asia/Shanghai}")
    public void runAttachmentManifestExport() {
        if (!properties.isEnabled() || !properties.getAttachmentManifestExport().isEnabled()) {
            return;
        }
        if (!attachmentManifestExportRunning.compareAndSet(false, true)) {
            log.warn("跳过附件恢复清单导出：上一轮仍在执行");
            return;
        }
        try {
            attachmentManifestExportService.exportDaily();
        } catch (Exception ex) {
            log.error("附件恢复清单导出失败", ex);
        } finally {
            attachmentManifestExportRunning.set(false);
        }
    }
}
