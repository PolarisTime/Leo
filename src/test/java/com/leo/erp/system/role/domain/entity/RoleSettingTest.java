package com.leo.erp.system.role.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSettingTest {

    @Test
    void shouldSetAndGetAllFields() {
        RoleSetting role = new RoleSetting();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("超级管理员");
        role.setRoleType("系统角色");
        role.setDataScope("ALL");
        role.setPermissionCount(100);
        role.setPermissionSummary("sys:*:*");
        role.setUserCount(5);
        role.setParentId(0L);
        role.setStatus("启用");
        role.setRemark("备注");

        assertThat(role.getId()).isEqualTo(1L);
        assertThat(role.getRoleCode()).isEqualTo("ADMIN");
        assertThat(role.getRoleName()).isEqualTo("超级管理员");
        assertThat(role.getRoleType()).isEqualTo("系统角色");
        assertThat(role.getDataScope()).isEqualTo("ALL");
        assertThat(role.getPermissionCount()).isEqualTo(100);
        assertThat(role.getPermissionSummary()).isEqualTo("sys:*:*");
        assertThat(role.getUserCount()).isEqualTo(5);
        assertThat(role.getParentId()).isEqualTo(0L);
        assertThat(role.getStatus()).isEqualTo("启用");
        assertThat(role.getRemark()).isEqualTo("备注");
    }

    @Test
    void shouldHandleNullValues() {
        RoleSetting role = new RoleSetting();
        role.setId(null);
        role.setRoleCode(null);
        role.setRoleName(null);
        role.setRoleType(null);
        role.setDataScope(null);
        role.setPermissionCount(null);
        role.setPermissionSummary(null);
        role.setUserCount(null);
        role.setParentId(null);
        role.setStatus(null);
        role.setRemark(null);

        assertThat(role.getId()).isNull();
        assertThat(role.getRoleCode()).isNull();
        assertThat(role.getRoleName()).isNull();
        assertThat(role.getRoleType()).isNull();
        assertThat(role.getDataScope()).isNull();
        assertThat(role.getPermissionCount()).isNull();
        assertThat(role.getPermissionSummary()).isNull();
        assertThat(role.getUserCount()).isNull();
        assertThat(role.getParentId()).isNull();
        assertThat(role.getStatus()).isNull();
        assertThat(role.getRemark()).isNull();
    }
}
