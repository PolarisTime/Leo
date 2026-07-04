package com.leo.erp.system.database.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseBackupYamlTest {

    @Test
    void productionYamlDisablesDatabaseImportAndScheduledBackupByDefault() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var propertySource = loader.load("application-prod", new ClassPathResource("application-prod.yml")).getFirst();

        assertThat(propertySource.getProperty("leo.database.import.enabled"))
                .isEqualTo("${LEO_DATABASE_IMPORT_ENABLED:false}");
        assertThat(propertySource.getProperty("leo.maintenance.database-backup.enabled"))
                .isEqualTo("${LEO_MAINTENANCE_DATABASE_BACKUP_ENABLED:false}");
    }
}
