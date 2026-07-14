package com.leo.erp.auth.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void shouldSetAndGetAllFields() {
        UserRole userRole = new UserRole();
        userRole.setId(1L);
        userRole.setUserId(100L);
        userRole.setRoleId(200L);

        assertThat(userRole.getId()).isEqualTo(1L);
        assertThat(userRole.getUserId()).isEqualTo(100L);
        assertThat(userRole.getRoleId()).isEqualTo(200L);
    }

    @Test
    void shouldHandleNullValues() {
        UserRole userRole = new UserRole();
        userRole.setId(null);
        userRole.setUserId(null);
        userRole.setRoleId(null);

        assertThat(userRole.getId()).isNull();
        assertThat(userRole.getUserId()).isNull();
        assertThat(userRole.getRoleId()).isNull();
    }
}
