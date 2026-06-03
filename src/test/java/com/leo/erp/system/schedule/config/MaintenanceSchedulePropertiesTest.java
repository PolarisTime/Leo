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
}
