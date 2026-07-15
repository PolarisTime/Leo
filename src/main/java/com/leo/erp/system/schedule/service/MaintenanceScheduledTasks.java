package com.leo.erp.system.schedule.service;

import com.leo.erp.attachment.service.AttachmentManifestExportService;
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
    private final AttachmentManifestExportService attachmentManifestExportService;
    private final AtomicBoolean redisCacheHealthCheckRunning = new AtomicBoolean(false);
    private final AtomicBoolean attachmentManifestExportRunning = new AtomicBoolean(false);

    public MaintenanceScheduledTasks(MaintenanceScheduleProperties properties,
                                     RedisCacheHealthCheckService redisCacheHealthCheckService,
                                     AttachmentManifestExportService attachmentManifestExportService) {
        this.properties = properties;
        this.redisCacheHealthCheckService = redisCacheHealthCheckService;
        this.attachmentManifestExportService = attachmentManifestExportService;
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
