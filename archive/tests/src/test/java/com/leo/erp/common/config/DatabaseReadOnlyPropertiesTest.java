package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseReadOnlyPropertiesTest {

    @Test
    void shouldCreateWithReadOnlyTrue() {
        DatabaseReadOnlyProperties properties = new DatabaseReadOnlyProperties(true);

        assertThat(properties.readOnly()).isTrue();
    }

    @Test
    void shouldCreateWithReadOnlyFalse() {
        DatabaseReadOnlyProperties properties = new DatabaseReadOnlyProperties(false);

        assertThat(properties.readOnly()).isFalse();
    }

    @Test
    void shouldImplementEquals() {
        DatabaseReadOnlyProperties p1 = new DatabaseReadOnlyProperties(true);
        DatabaseReadOnlyProperties p2 = new DatabaseReadOnlyProperties(true);

        assertThat(p1).isEqualTo(p2);
    }

    @Test
    void shouldImplementHashCode() {
        DatabaseReadOnlyProperties p1 = new DatabaseReadOnlyProperties(true);
        DatabaseReadOnlyProperties p2 = new DatabaseReadOnlyProperties(true);

        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenReadOnlyDiffers() {
        DatabaseReadOnlyProperties p1 = new DatabaseReadOnlyProperties(true);
        DatabaseReadOnlyProperties p2 = new DatabaseReadOnlyProperties(false);

        assertThat(p1).isNotEqualTo(p2);
    }
}
