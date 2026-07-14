package com.leo.erp.auth.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    @Test
    void shouldHaveCorrectEnumValues() {
        assertThat(UserStatus.values()).hasSize(2);
        assertThat(UserStatus.valueOf("NORMAL")).isEqualTo(UserStatus.NORMAL);
        assertThat(UserStatus.valueOf("DISABLED")).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void shouldSupportEqualityComparison() {
        assertThat(UserStatus.NORMAL).isEqualTo(UserStatus.NORMAL);
        assertThat(UserStatus.DISABLED).isEqualTo(UserStatus.DISABLED);
        assertThat(UserStatus.NORMAL).isNotEqualTo(UserStatus.DISABLED);
    }
}
