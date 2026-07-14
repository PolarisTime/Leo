package com.leo.erp.security.totp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpPropertiesTest {

    @Test
    void shouldCreateWithConstructor() {
        TotpProperties properties = new TotpProperties("LeoERP", "encryption-key-123");

        assertThat(properties.getIssuer()).isEqualTo("LeoERP");
        assertThat(properties.getEncryptionKey()).isEqualTo("encryption-key-123");
    }

    @Test
    void shouldCreateWithDefaultConstructor() {
        TotpProperties properties = new TotpProperties();

        assertThat(properties.getIssuer()).isNull();
        assertThat(properties.getEncryptionKey()).isNull();
    }

    @Test
    void shouldSetAndGetIssuer() {
        TotpProperties properties = new TotpProperties();
        properties.setIssuer("LeoERP");

        assertThat(properties.getIssuer()).isEqualTo("LeoERP");
    }

    @Test
    void shouldSetAndGetEncryptionKey() {
        TotpProperties properties = new TotpProperties();
        properties.setEncryptionKey("encryption-key-123");

        assertThat(properties.getEncryptionKey()).isEqualTo("encryption-key-123");
    }

    @Test
    void shouldHaveConfigurationPropertiesAnnotation() {
        assertThat(TotpProperties.class.getAnnotation(org.springframework.boot.context.properties.ConfigurationProperties.class))
                .isNotNull()
                .satisfies(annotation -> assertThat(annotation.prefix()).isEqualTo("leo.security.totp"));
    }

    @Test
    void shouldHaveValidatedAnnotation() {
        assertThat(TotpProperties.class.getAnnotation(org.springframework.validation.annotation.Validated.class)).isNotNull();
    }
}