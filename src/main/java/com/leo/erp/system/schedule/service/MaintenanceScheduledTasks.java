package com.leo.erp.system.schedule.service;

import com.leo.erp.attachment.api.AttachmentManifestExporter;
import com.leo.erp.system.operationlog.partition.OperationLogPartitionCleanupService;
import com.leo.erp.system.schedule.config.MaintenanceScheduleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class MaintenanceScheduledTasks {

    private final MaintenanceScheduleProperties properties;
    private final RedisCacheHealthCheckService redisCacheHealthCheckService;
    private final AttachmentManifestExporter attachmentManifestExporter;
    private final OperationLogPartitionCleanupService operationLogPartitionCleanupService;
    private final AtomicBoolean redisCacheHealthCheckRunning = new AtomicBoolean(false);
    private final AtomicBoolean attachmentManifestExportRunning = new AtomicBoolean(false);
    private final AtomicBoolean operationLogPartitionCleanupRunning = new AtomicBoolean(false);

    public MaintenanceScheduledTasks(MaintenanceScheduleProperties properties,
                                     RedisCacheHealthCheckService redisCacheHealthCheckService,
                                     AttachmentManifestExporter attachmentManifestExporter,
                                     OperationLogPartitionCleanupService operationLogPartitionCleanupService) {
        this.properties = properties;
        this.redisCacheHealthCheckService = redisCacheHealthCheckService;
        this.attachmentManifestExporter = attachmentManifestExporter;
        this.operationLogPartitionCleanupService = operationLogPartitionCleanupService;
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
            attachmentManifestExporter.exportDaily();
        } catch (Exception ex) {
            log.error("附件恢复清单导出失败", ex);
        } finally {
            attachmentManifestExportRunning.set(false);
        }
    }

    /**
     * 操作日志月分区清理：删除「超出保留窗口且为空」的分区。
     *
     * <p>与 V205 一次性清理同一口径（当前月 ± retention-months）；非空分区保留待人工归档，
     * 判定与窗口逻辑见 {@link OperationLogPartitionCleanupService}。</p>
     */
    @Scheduled(
            cron = "${leo.maintenance.operation-log-partition-cleanup.cron:0 20 3 1 * *}",
            zone = "${leo.maintenance.zone:Asia/Shanghai}")
    public void runOperationLogPartitionCleanup() {
        if (!properties.isEnabled() || !properties.getOperationLogPartitionCleanup().isEnabled()) {
            return;
        }
        if (!operationLogPartitionCleanupRunning.compareAndSet(false, true)) {
            log.warn("跳过操作日志分区清理：上一轮仍在执行");
            return;
        }
        try {
            operationLogPartitionCleanupService.cleanupExpiredEmptyPartitions();
        } catch (Exception ex) {
            log.error("操作日志分区清理失败", ex);
        } finally {
            operationLogPartitionCleanupRunning.set(false);
        }
    }
}
