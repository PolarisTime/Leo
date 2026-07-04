package com.leo.erp.system.schedule.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceSchedulePropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        var properties = new MaintenanceScheduleProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getZone()).isEqualTo("Asia/Shanghai");
        assertThat(properties.getDatabaseBackup()).isNotNull();
        assertThat(properties.getOperationLogArchive()).isNotNull();
        assertThat(properties.getExportTaskCleanup()).isNotNull();
    }

    @Test
    void shouldSetAndGetEnabled() {
        var properties = new MaintenanceScheduleProperties();
        properties.setEnabled(false);

        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void shouldSetAndGetZone() {
        var properties = new MaintenanceScheduleProperties();
        properties.setZone("UTC");

        assertThat(properties.getZone()).isEqualTo("UTC");
    }

    @Test
    void shouldHaveDefaultBackupTaskValues() {
        var properties = new MaintenanceScheduleProperties();
        var backup = properties.getDatabaseBackup();

        assertThat(backup.isEnabled()).isTrue();
        assertThat(backup.getCron()).isEqualTo("0 15 2 * * *");
        assertThat(backup.getRetentionDays()).isEqualTo(30);
    }

    @Test
    void shouldHaveDefaultArchiveTaskValues() {
        var properties = new MaintenanceScheduleProperties();
        var archive = properties.getOperationLogArchive();

        assertThat(archive.isEnabled()).isTrue();
        assertThat(archive.getCron()).isEqualTo("0 30 2 * * *");
        assertThat(archive.getRetentionDays()).isEqualTo(30);
        assertThat(archive.getBatchSize()).isEqualTo(1000);
        assertThat(archive.getArchivePath()).isEqualTo("/tmp/leo/operation-log-archives");
    }

    @Test
    void shouldHaveDefaultCleanupTaskValues() {
        var properties = new MaintenanceScheduleProperties();
        var cleanup = properties.getExportTaskCleanup();

        assertThat(cleanup.isEnabled()).isTrue();
        assertThat(cleanup.getCron()).isEqualTo("0 45 2 * * *");
    }

    @Test
    void shouldReplaceNestedTaskProperties() {
        var properties = new MaintenanceScheduleProperties();
        var backup = new MaintenanceScheduleProperties.BackupTask();
        var archive = new MaintenanceScheduleProperties.OperationLogArchiveTask();
        var cleanup = new MaintenanceScheduleProperties.ExportTaskCleanup();
        var redis = new MaintenanceScheduleProperties.RedisCacheHealthCheckTask();
        var manifest = new MaintenanceScheduleProperties.AttachmentManifestExportTask();

        properties.setDatabaseBackup(backup);
        properties.setOperationLogArchive(archive);
        properties.setExportTaskCleanup(cleanup);
        properties.setRedisCacheHealthCheck(redis);
        properties.setAttachmentManifestExport(manifest);

        assertThat(properties.getDatabaseBackup()).isSameAs(backup);
        assertThat(properties.getOperationLogArchive()).isSameAs(archive);
        assertThat(properties.getExportTaskCleanup()).isSameAs(cleanup);
        assertThat(properties.getRedisCacheHealthCheck()).isSameAs(redis);
        assertThat(properties.getAttachmentManifestExport()).isSameAs(manifest);
    }

    @Test
    void shouldSetAndGetNestedTaskValues() {
        var backup = new MaintenanceScheduleProperties.BackupTask();
        backup.setEnabled(false);
        backup.setCron("0 0 1 * * *");
        backup.setRetentionDays(7);

        var archive = new MaintenanceScheduleProperties.OperationLogArchiveTask();
        archive.setEnabled(false);
        archive.setCron("0 10 1 * * *");
        archive.setRetentionDays(14);
        archive.setBatchSize(50);
        archive.setArchivePath("/tmp/archive");

        var cleanup = new MaintenanceScheduleProperties.ExportTaskCleanup();
        cleanup.setEnabled(false);
        cleanup.setCron("0 20 1 * * *");

        var redis = new MaintenanceScheduleProperties.RedisCacheHealthCheckTask();
        redis.setEnabled(false);
        redis.setCron("0 */10 * * * *");

        var manifest = new MaintenanceScheduleProperties.AttachmentManifestExportTask();
        manifest.setEnabled(false);
        manifest.setCron("0 30 1 * * *");

        assertThat(backup.isEnabled()).isFalse();
        assertThat(backup.getCron()).isEqualTo("0 0 1 * * *");
        assertThat(backup.getRetentionDays()).isEqualTo(7);
        assertThat(archive.isEnabled()).isFalse();
        assertThat(archive.getCron()).isEqualTo("0 10 1 * * *");
        assertThat(archive.getRetentionDays()).isEqualTo(14);
        assertThat(archive.getBatchSize()).isEqualTo(50);
        assertThat(archive.getArchivePath()).isEqualTo("/tmp/archive");
        assertThat(cleanup.isEnabled()).isFalse();
        assertThat(cleanup.getCron()).isEqualTo("0 20 1 * * *");
        assertThat(redis.isEnabled()).isFalse();
        assertThat(redis.getCron()).isEqualTo("0 */10 * * * *");
        assertThat(manifest.isEnabled()).isFalse();
        assertThat(manifest.getCron()).isEqualTo("0 30 1 * * *");
    }
}
