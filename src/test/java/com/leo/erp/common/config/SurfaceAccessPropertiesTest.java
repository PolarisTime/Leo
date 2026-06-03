package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceAccessPropertiesTest {

    private final SurfaceAccessProperties properties = new SurfaceAccessProperties();

    @Test
    void shouldHaveDocsSection() {
        assertThat(properties.getDocs()).isNotNull();
    }

    @Test
    void shouldHaveHealthSection() {
        assertThat(properties.getHealth()).isNotNull();
    }

    @Test
    void shouldHaveDocsPublicAccessDisabledByDefault() {
        assertThat(properties.getDocs().isPublicAccessEnabled()).isFalse();
    }

    @Test
    void shouldHaveHealthPublicAccessEnabledByDefault() {
        assertThat(properties.getHealth().isPublicAccessEnabled()).isTrue();
    }

    @Test
    void shouldSetDocsPublicAccess() {
        properties.getDocs().setPublicAccessEnabled(true);
        assertThat(properties.getDocs().isPublicAccessEnabled()).isTrue();
    }

    @Test
    void shouldSetHealthPublicAccess() {
        properties.getHealth().setPublicAccessEnabled(false);
        assertThat(properties.getHealth().isPublicAccessEnabled()).isFalse();
    }
}
