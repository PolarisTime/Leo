package com.leo.erp.system.database.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseImportPropertiesTest {

    @Test
    void shouldEnableImportByDefault() {
        var properties = new DatabaseImportProperties();

        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void shouldSetAndGetEnabled() {
        var properties = new DatabaseImportProperties();
        properties.setEnabled(false);

        assertThat(properties.isEnabled()).isFalse();
    }
}
