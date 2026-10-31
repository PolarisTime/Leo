package com.leo.erp.system.schedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leo.maintenance")
public class MaintenanceScheduleProperties {

    private boolean enabled = true;
    private String zone = "Asia/Shanghai";
    private RedisCacheHealthCheckTask redisCacheHealthCheck = new RedisCacheHealthCheckTask();
    private AttachmentManifestExportTask attachmentManifestExport = new AttachmentManifestExportTask();
    private OperationLogPartitionCleanupTask operationLogPartitionCleanup = new OperationLogPartitionCleanupTask();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public RedisCacheHealthCheckTask getRedisCacheHealthCheck() {
        return redisCacheHealthCheck;
    }

    public void setRedisCacheHealthCheck(RedisCacheHealthCheckTask redisCacheHealthCheck) {
        this.redisCacheHealthCheck = redisCacheHealthCheck;
    }

    public AttachmentManifestExportTask getAttachmentManifestExport() {
        return attachmentManifestExport;
    }

    public void setAttachmentManifestExport(AttachmentManifestExportTask attachmentManifestExport) {
        this.attachmentManifestExport = attachmentManifestExport;
    }

    public OperationLogPartitionCleanupTask getOperationLogPartitionCleanup() {
        return operationLogPartitionCleanup;
    }

    public void setOperationLogPartitionCleanup(OperationLogPartitionCleanupTask operationLogPartitionCleanup) {
        this.operationLogPartitionCleanup = operationLogPartitionCleanup;
    }

    public static class RedisCacheHealthCheckTask {
        private boolean enabled = true;
        private String cron = "0 */5 * * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public static class AttachmentManifestExportTask {
        private boolean enabled = true;
        private String cron = "0 5 2 * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    /**
     * 操作日志月分区清理任务。
     *
     * <p>默认每月 1 日 03:20 执行：只删「超出保留窗口且为空」的分区。窗口过去侧沿用
     * {@code leo.observability.operation-log.retention-months}、未来侧沿用
     * {@code partition-lookahead-months}，默认配置下与 V205 的「当前月 ±12 个月」等价。</p>
     */
    public static class OperationLogPartitionCleanupTask {
        private boolean enabled = true;
        private String cron = "0 20 3 1 * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
