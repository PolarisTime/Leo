package com.leo.erp.system.schedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leo.maintenance")
public class MaintenanceScheduleProperties {

    private boolean enabled = true;
    private String zone = "Asia/Shanghai";
    private OperationLogArchiveTask operationLogArchive = new OperationLogArchiveTask();
    private RedisCacheHealthCheckTask redisCacheHealthCheck = new RedisCacheHealthCheckTask();
    private AttachmentManifestExportTask attachmentManifestExport = new AttachmentManifestExportTask();

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

    public OperationLogArchiveTask getOperationLogArchive() {
        return operationLogArchive;
    }

    public void setOperationLogArchive(OperationLogArchiveTask operationLogArchive) {
        this.operationLogArchive = operationLogArchive;
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

    public static class OperationLogArchiveTask {
        private boolean enabled = true;
        private String cron = "0 30 2 * * *";
        private int retentionDays = 30;
        private int batchSize = 1000;
        private String archivePath = "/tmp/leo/operation-log-archives";

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

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public String getArchivePath() {
            return archivePath;
        }

        public void setArchivePath(String archivePath) {
            this.archivePath = archivePath;
        }
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
}
