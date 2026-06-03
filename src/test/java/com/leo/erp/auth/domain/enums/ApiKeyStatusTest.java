package com.leo.erp.auth.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyStatusTest {

    @Test
    void shouldReturnDisplayNameForActive() {
        assertThat(ApiKeyStatus.ACTIVE.displayName()).isEqualTo("有效");
    }

    @Test
    void shouldReturnDisplayNameForDisabled() {
        assertThat(ApiKeyStatus.DISABLED.displayName()).isEqualTo("已禁用");
    }

    @Test
    void shouldFindByDisplayName() {
        assertThat(ApiKeyStatus.fromDisplayName("有效")).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(ApiKeyStatus.fromDisplayName("已禁用")).isEqualTo(ApiKeyStatus.DISABLED);
    }

    @Test
    void shouldReturnNullForNullDisplayName() {
        assertThat(ApiKeyStatus.fromDisplayName((String) null)).isNull();
    }

    @Test
    void shouldFindByDisplayNameWithWhitespace() {
        assertThat(ApiKeyStatus.fromDisplayName("  有效  ")).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    void shouldThrowExceptionForUnknownDisplayName() {
        assertThatThrownBy(() -> ApiKeyStatus.fromDisplayName("未知状态"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown ApiKeyStatus display name");
    }

    @Test
    void shouldHaveCorrectEnumValues() {
        assertThat(ApiKeyStatus.values()).hasSize(2);
        assertThat(ApiKeyStatus.valueOf("ACTIVE")).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(ApiKeyStatus.valueOf("DISABLED")).isEqualTo(ApiKeyStatus.DISABLED);
    }
}
