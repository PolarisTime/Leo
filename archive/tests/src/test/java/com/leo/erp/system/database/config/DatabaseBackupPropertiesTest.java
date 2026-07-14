package com.leo.erp.system.database.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseBackupPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        var properties = new DatabaseBackupProperties();

        assertThat(properties.isAutoBackupBeforeImport()).isTrue();
        assertThat(properties.getStoragePath()).isEqualTo("/tmp/leo/database-backups");
        assertThat(properties.getDownloadExpireDays()).isEqualTo(7);
        assertThat(properties.getPgDumpCommand()).isEqualTo("pg_dump");
        assertThat(properties.getPsqlCommand()).isEqualTo("psql");
    }

    @Test
    void shouldSetAndGetAutoBackupBeforeImport() {
        var properties = new DatabaseBackupProperties();
        properties.setAutoBackupBeforeImport(false);

        assertThat(properties.isAutoBackupBeforeImport()).isFalse();
    }

    @Test
    void shouldSetAndGetStoragePath() {
        var properties = new DatabaseBackupProperties();
        properties.setStoragePath("/custom/path");

        assertThat(properties.getStoragePath()).isEqualTo("/custom/path");
    }

    @Test
    void shouldSetAndGetDownloadExpireDays() {
        var properties = new DatabaseBackupProperties();
        properties.setDownloadExpireDays(14);

        assertThat(properties.getDownloadExpireDays()).isEqualTo(14);
    }

    @Test
    void shouldSetAndGetPgDumpCommand() {
        var properties = new DatabaseBackupProperties();
        properties.setPgDumpCommand("/usr/bin/pg_dump");

        assertThat(properties.getPgDumpCommand()).isEqualTo("/usr/bin/pg_dump");
    }

    @Test
    void shouldSetAndGetPsqlCommand() {
        var properties = new DatabaseBackupProperties();
        properties.setPsqlCommand("/usr/bin/psql");

        assertThat(properties.getPsqlCommand()).isEqualTo("/usr/bin/psql");
    }
}
